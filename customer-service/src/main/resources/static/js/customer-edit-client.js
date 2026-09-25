// loads and updates the customer selected by the url id
const params = new URLSearchParams(window.location.search);
const customerId = params.get("id");

const form = document.getElementById("customerForm");

// loads the selected customer's values into the form
async function loadCustomer() {
    if (!customerId) {
        showError("Customer ID is missing.");
        return;
    }

    try {
        const response = await fetch(
            `/api/v1/customers/${customerId}`
        );

        if (!response.ok) {
            const error = await response.json();
            showError(error.message);
            return;
        }

        const customer = await response.json();

        document.getElementById("name").value =
            customer.name ?? "";

        document.getElementById("contactName").value =
            customer.contactName ?? "";

        document.getElementById("phone").value =
            customer.phone ?? "";

        document.getElementById("active").checked =
            customer.active;

    } catch (error) {
        showError("Unable to communicate with the server.");
    }
}

// saves the edited customer details
form.addEventListener("submit", async function (event) {
    event.preventDefault();
    clearErrors();

    const customer = {
        name: document.getElementById("name").value,
        contactName: document.getElementById("contactName").value,
        phone: document.getElementById("phone").value,
        active: document.getElementById("active").checked
    };

    try {
        const response = await fetch(
            `/api/v1/customers/${customerId}`,
            {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(customer)
            }
        );

        if (!response.ok) {
            const error = await response.json();
            showError(error.message);
            return;
        }

        window.location.href = "/customers-client.html";

    } catch (error) {

        showError("Unable to communicate with the server.");
    }
});

// displays a validation or communication error
function showError(message) {
    const errorContainer =
        document.getElementById("errorContainer");

    const errorList =
        document.getElementById("errorList");

    const item = document.createElement("li");
    item.textContent = message;

    errorList.appendChild(item);
    errorContainer.hidden = false;
}

// removes errors shown by a previous operation
function clearErrors() {
    document.getElementById("errorList").innerHTML = "";
    document.getElementById("errorContainer").hidden = true;
}

// loads the selected customer when the page opens
loadCustomer();
