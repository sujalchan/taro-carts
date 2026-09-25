// submits a new customer and displays validation errors
const form = document.getElementById("customerForm");

// creates a customer from the form values
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
        const response = await fetch("/api/v1/customers", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(customer)
        });

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

// removes errors shown by a previous submission
function clearErrors() {
    document.getElementById("errorList").innerHTML = "";
    document.getElementById("errorContainer").hidden = true;
}
