const dispatcherForm = document.getElementById("dispatcherForm");

dispatcherForm.addEventListener("submit", async event => {
    event.preventDefault();
    clearErrors();

    const dispatcher = {
        name: document.getElementById("name").value,
        contactName: document.getElementById("contactName").value,
        phone: document.getElementById("phone").value,
        address: document.getElementById("address").value.trim(),
        active: document.getElementById("active").checked
    };

    try {
        const response = await fetch("/api/v1/dispatchers", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(dispatcher)
        });
        if (!response.ok) {
            const error = await response.json();
            showError(error.message || `Unable to create dispatcher. HTTP ${response.status}`);
            return;
        }
        window.location.href = "/dispatchers-client.html";
    } catch (_) {
        showError("Unable to communicate with the server.");
    }
});

function showError(message) {
    const item = document.createElement("li");
    item.textContent = message;
    document.getElementById("errorList").appendChild(item);
    document.getElementById("errorContainer").hidden = false;
}

function clearErrors() {
    document.getElementById("errorList").replaceChildren();
    document.getElementById("errorContainer").hidden = true;
}
