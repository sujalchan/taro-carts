document.querySelectorAll(".app-nav").forEach((nav) => {
    const toggle = nav.querySelector(".app-nav__toggle");
    const media = window.matchMedia("(max-width: 850px)");

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
