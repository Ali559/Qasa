use axum::middleware::Next;
use axum::response::Response;
use axum::http::Request;

pub async fn count_requests(request: Request<axum::body::Body>, next: Next) -> Response {
    crate::metrics::record_http_request();
    next.run(request).await
}
