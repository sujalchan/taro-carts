// Adds Spring Security's CSRF token to browser writes while keeping fetch's API.
window.apiFetch = function (input, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    if (!["POST", "PUT", "PATCH", "DELETE"].includes(method)) {
        return fetch(input, options);
    }
    const cookie = document.cookie.split("; ").find(part => part.startsWith("XSRF-TOKEN="));
    const token = cookie ? decodeURIComponent(cookie.substring("XSRF-TOKEN=".length)) : null;
    const headers = new Headers(options.headers);
    if (token) headers.set("X-XSRF-TOKEN", token);
    return fetch(input, { ...options, headers });
};
