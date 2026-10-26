const dispatcherParams = new URLSearchParams(window.location.search);
const dispatcherId = dispatcherParams.get("id");
const dispatcherForm = document.getElementById("dispatcherForm");

async function loadDispatcher() {
    if (!dispatcherId) {
        showError("Dispatcher ID is missing.");
        dispatcherForm.hidden = true;
        return;
    }

    try {
        const response = await fetch(`/api/v1/dispatchers/${encodeURIComponent(dispatcherId)}`);
        if (!response.ok) {
            const error = await response.json();
            showError(error.message || `Unable to load dispatcher. HTTP ${response.status}`);
            dispatcherForm.hidden = true;
            return;
        }

        const dispatcher = await response.json();
        document.getElementById("name").value = dispatcher.name ?? "";
        document.getElementById("contactName").value = dispatcher.contactName ?? "";
        document.getElementById("phone").value = dispatcher.phone ?? "";
        document.getElementById("address").value = dispatcher.address ?? "";
        document.getElementById("active").checked = dispatcher.active === true;
    } catch (_) {
        showError("Unable to communicate with the server.");
        dispatcherForm.hidden = true;
    }
}

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
        const response = await fetch(`/api/v1/dispatchers/${encodeURIComponent(dispatcherId)}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(dispatcher)
        });
        if (!response.ok) {
            const error = await response.json();
            showError(error.message || `Unable to update dispatcher. HTTP ${response.status}`);
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

loadDispatcher();
