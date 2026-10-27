document.querySelectorAll(".app-nav").forEach((nav) => {
    const links = nav.querySelector(".app-nav__links");
    if (!links) return;

    links.id ||= "service-navigation";
    const toggle = document.createElement("button");
    toggle.className = "app-nav__toggle";
    toggle.type = "button";
    toggle.textContent = "☰ Menu";
    toggle.setAttribute("aria-controls", links.id);
    toggle.setAttribute("aria-expanded", "false");
    nav.insertBefore(toggle, links);

    const media = window.matchMedia("(max-width: 850px)");
    const closeMenu = () => toggle.setAttribute("aria-expanded", "false");
    toggle.addEventListener("click", () => {
        toggle.setAttribute("aria-expanded",
            String(toggle.getAttribute("aria-expanded") !== "true"));
    });
    nav.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            closeMenu();
            toggle.focus();
        }
    });
    media.addEventListener("change", closeMenu);
    nav.classList.add("app-nav--enhanced");
});
