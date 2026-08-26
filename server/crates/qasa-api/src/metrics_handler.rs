use axum::response::IntoResponse;

pub async fn prometheus_metrics() -> impl IntoResponse {
    crate::metrics::render_metrics()
}
