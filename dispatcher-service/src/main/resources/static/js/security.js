window.taroCsrfHeaders = function () {
    const name = "DISPATCHER_CSRF=";
    const cookie = document.cookie.split(";").map(value => value.trim()).find(value => value.startsWith(name));
    return { "X-CSRF-Token": cookie ? decodeURIComponent(cookie.slice(name.length)) : "" };
};

