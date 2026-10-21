document.querySelectorAll(".app-nav").forEach((nav) => {
    const toggle = nav.querySelector(".app-nav__toggle");
    const media = window.matchMedia("(max-width: 850px)");
    const logoutForm = nav.querySelector(".logout-form");

    logoutForm?.addEventListener("submit", () => {
        const tokenField = logoutForm.querySelector('input[name="_csrf"]');
        const cookieName = "TARO_TARO_XSRF_TOKEN=";
        const cookie = document.cookie.split("; ").find((item) => item.startsWith(cookieName));
        if (cookie) tokenField.value = decodeURIComponent(cookie.slice(cookieName.length));
    });

    function closeMenu() {
        toggle.setAttribute("aria-expanded", "false");
    }

    toggle.addEventListener("click", () => {
        const isOpen = toggle.getAttribute("aria-expanded") === "true";
        toggle.setAttribute("aria-expanded", String(!isOpen));
    });

    nav.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            closeMenu();
            toggle.focus();
        }
    });

    media.addEventListener("change", closeMenu);
});
