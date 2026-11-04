//! The one place a payment turns into an entitlement.
//!
//! The webhook handler and the worker's retry job used to each carry their own copy of
//! this logic. The worker's copy skipped the amount check, so a webhook the API had
//! rejected for under-reporting the amount was later settled by the retry job anyway.
//! Both copies also ran "is it already paid?", "grant the subscription" and "mark it
//! paid" as separate statements, so two deliveries of the same webhook could both pass
//! the first check and grant two subscriptions.
//!
//! Everything below runs in one transaction with the payment row locked.

use sqlx::PgPool;
use uuid::Uuid;

use crate::repos::payment::{PaymentRow, PAYMENT_COLS};
use crate::repos::subscription::{activate_subscription, SubscriptionRow};

/// What the gateway says happened to an order, after it verified the webhook.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SettleStatus {
    Success,
    Failed,
    Pending,
}

impl SettleStatus {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Success => "success",
            Self::Failed => "failed",
            Self::Pending => "pending",
        }
    }

    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "success" => Some(Self::Success),
            "failed" => Some(Self::Failed),
            "pending" => Some(Self::Pending),
            _ => None,
        }
    }
}

#[derive(Debug)]
pub enum SettleOutcome {
    /// The payment was marked paid and this subscription granted.
    Settled {
        payment: PaymentRow,
        subscription: SubscriptionRow,
    },
    /// The payment moved from pending to failed.
    Failed { payment: PaymentRow },
    /// Nothing to do: already paid, already failed, or the gateway still says pending.
    Unchanged,
    /// The gateway reported a different amount from the one charged. Nothing granted.
    AmountMismatch { reported: i32, expected: i32 },
    /// No payment with this order id exists for this gateway.
    NotFound,
    /// The money arrived after the user deleted their account. Nothing is granted; the
    /// payment is left as it was for staff to refund.
    AccountDeleted { payment: PaymentRow },
}

impl SettleOutcome {
    /// Whether the webhook event can be marked processed. Only a still-pending order is
    /// worth retrying.
    pub fn is_terminal(&self, status: SettleStatus) -> bool {
        !matches!(self, Self::Unchanged if status == SettleStatus::Pending)
    }

    /// A short reason for the webhook audit log, for outcomes that did not settle.
    pub fn error_message(&self) -> Option<String> {
        match self {
            Self::AmountMismatch { reported, expected } => Some(format!(
                "webhook amount {reported} does not match the charged amount {expected}"
            )),
            Self::NotFound => Some("payment not found".into()),
            Self::AccountDeleted { .. } => Some("account deleted; refund required".into()),
            _ => None,
        }
    }
}

pub async fn settle_payment(
    pool: &PgPool,
    gateway: &str,
    gateway_order_id: &str,
    status: SettleStatus,
    reported_amount_iqd: Option<i32>,
) -> anyhow::Result<SettleOutcome> {
    settle_payment_with_token(pool, gateway, gateway_order_id, status, reported_amount_iqd, None)
        .await
}

/// [`settle_payment`], also saving the reusable `payment_token` the gateway returned.
///
/// The token is kept only when the payment had auto-renew on: leaving it on at checkout
/// is the customer's consent to be charged again. It is saved in the same transaction
/// as the grant, so a paid renewal and its payment method never disagree.
pub async fn settle_payment_with_token(
    pool: &PgPool,
    gateway: &str,
    gateway_order_id: &str,
    status: SettleStatus,
    reported_amount_iqd: Option<i32>,
    payment_token: Option<&str>,
) -> anyhow::Result<SettleOutcome> {
    let mut tx = pool.begin().await?;

    let payment = sqlx::query_as::<_, PaymentRow>(
        &format!(
        "SELECT {PAYMENT_COLS} FROM payments WHERE gateway_order_id = $1 FOR UPDATE"
    ),
    )
    .bind(gateway_order_id)
    .fetch_optional(&mut *tx)
    .await?;

    // A webhook from one gateway must not be able to settle another gateway's order.
    let Some(payment) = payment.filter(|p| p.gateway == gateway) else {
        return Ok(SettleOutcome::NotFound);
    };

    // `pending`, `failed` and `expired` (abandoned by the expiry job) can all still be
    // settled: a confirmation that arrives late means the money moved.
    if payment.status == "success" {
        return Ok(SettleOutcome::Unchanged);
    }

    match status {
        SettleStatus::Pending => Ok(SettleOutcome::Unchanged),
        SettleStatus::Failed => {
            if payment.status == "failed" {
                return Ok(SettleOutcome::Unchanged);
            }
            let payment = mark(&mut tx, payment.id, "failed", None).await?;
            tx.commit().await?;
            Ok(SettleOutcome::Failed { payment })
        }
        SettleStatus::Success => {
            // A gateway that under-reports the amount must not unlock a full-price plan.
            if let Some(reported) = reported_amount_iqd {
                if reported != payment.amount_iqd {
                    return Ok(SettleOutcome::AmountMismatch {
                        reported,
                        expected: payment.amount_iqd,
                    });
                }
            }

            let deleted: bool = sqlx::query_scalar(
                "SELECT deleted_at IS NOT NULL FROM accounts WHERE id = $1",
            )
            .bind(payment.account_id)
            .fetch_one(&mut *tx)
            .await?;
            if deleted {
                return Ok(SettleOutcome::AccountDeleted { payment });
            }

            let subscription = activate_subscription(
                &mut tx,
                payment.account_id,
                payment.amount_iqd,
                &payment.plan_id,
                payment.auto_renew,
            )
            .await?;
            if let Some(token) = payment_token.filter(|_| payment.auto_renew) {
                crate::repos::payment_method::save(&mut tx, payment.account_id, gateway, token)
                    .await?;
            }
            let payment = mark(&mut tx, payment.id, "success", Some(subscription.id)).await?;
            tx.commit().await?;
            Ok(SettleOutcome::Settled {
                payment,
                subscription,
            })
        }
    }
}

async fn mark(
    conn: &mut sqlx::PgConnection,
    payment_id: Uuid,
    status: &str,
    subscription_id: Option<Uuid>,
) -> anyhow::Result<PaymentRow> {
    let row = sqlx::query_as::<_, PaymentRow>(&format!(
        r#"
        UPDATE payments
        SET status = $2,
            subscription_id = COALESCE($3, subscription_id),
            completed_at = now()
        WHERE id = $1
        RETURNING {PAYMENT_COLS}
        "#
    ))
    .bind(payment_id)
    .bind(status)
    .bind(subscription_id)
    .fetch_one(conn)
    .await?;
    Ok(row)
}
