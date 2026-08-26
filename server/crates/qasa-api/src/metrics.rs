use std::sync::OnceLock;

use metrics_exporter_prometheus::{PrometheusBuilder, PrometheusHandle};

static PROMETHEUS: OnceLock<PrometheusHandle> = OnceLock::new();

pub fn init_production_metrics() -> &'static PrometheusHandle {
    PROMETHEUS.get_or_init(|| {
        metrics::describe_counter!(
            "qasa_http_requests_total",
            "Total HTTP requests handled by qasa-api"
        );
        PrometheusBuilder::new()
            .install_recorder()
            .expect("install prometheus recorder")
    })
}

pub fn render_metrics() -> String {
    init_production_metrics().render()
}

pub fn record_http_request() {
    metrics::counter!("qasa_http_requests_total").increment(1);
}
