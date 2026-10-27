const navigationLinks = {
    admin: [
        ["Customers", "http://localhost:8081/customers"],
        ["Dispatchers", "http://localhost:8084/dispatchers"],
        ["Taro", "http://localhost:8083/taro-types"],
        ["Allocations", "http://localhost:8082/allocations"],
        ["Customer portal", "http://localhost:8081/portal/allocations"],
        ["Dispatcher portal", "http://localhost:8084/portal/deliveries"],
        ["Accounts", "http://localhost:8085/admin/accounts"],
        ["Sign out", "http://localhost:8085/logout"]
    ],
    customer: [
        ["My allocations", "http://localhost:8081/portal/allocations"],
        ["My Account", "http://localhost:8081/portal/profile"],
        ["Sign out", "http://localhost:8085/logout"]
    ],
    dispatcher: [
        ["My deliveries", "http://localhost:8084/portal/deliveries"],
        ["Sign out", "http://localhost:8085/logout"]
    ]
};

document.querySelectorAll(".app-nav").forEach((nav) => {
    const role = nav.dataset.navRole || "admin";
    const links = nav.querySelector(".app-nav__links");
    const brand = nav.querySelector(".app-nav__brand");
    if (!links || !brand) return;
    brand.textContent = "Taro Carts";
    brand.href = role === "customer" ? "http://localhost:8081/portal/allocations"
        : role === "dispatcher" ? "http://localhost:8084/portal/deliveries"
        : "http://localhost:8085/admin/accounts";
    links.id ||= "service-navigation";
    links.replaceChildren();
    for (const [label, destination] of navigationLinks[role] || navigationLinks.admin) {
        const link = document.createElement("a");
        link.href = destination;
        link.textContent = label;
        const target = new URL(destination);
        if (target.host === window.location.host &&
                (window.location.pathname === target.pathname ||
                 window.location.pathname.startsWith(target.pathname + "/"))) {
            link.setAttribute("aria-current", "page");
        }
        links.appendChild(link);
    }

    let toggle = nav.querySelector(".app-nav__toggle");
    if (!toggle) {
        toggle = document.createElement("button");
        toggle.className = "app-nav__toggle";
        toggle.type = "button";
        toggle.textContent = "☰ Menu";
        nav.insertBefore(toggle, links);
    }
    toggle.setAttribute("aria-controls", links.id);
    toggle.setAttribute("aria-expanded", "false");
    const media = window.matchMedia("(max-width: 850px)");
    function closeMenu() { toggle.setAttribute("aria-expanded", "false"); }
    toggle.addEventListener("click", () => {
        toggle.setAttribute("aria-expanded",
            String(toggle.getAttribute("aria-expanded") !== "true"));
    });
    nav.addEventListener("keydown", (event) => {
        if (event.key === "Escape") { closeMenu(); toggle.focus(); }
    });
    media.addEventListener("change", closeMenu);
});
