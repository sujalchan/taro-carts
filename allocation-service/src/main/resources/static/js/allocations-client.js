// lists, formats, and deletes weekly allocations
const statusElement = document.getElementById("status");
const allocationsContainer = document.getElementById("allocationsContainer");
const errorContainer = document.getElementById("errorContainer");
const errorList = document.getElementById("errorList");

// loads the initial allocation list
loadAllocations();

// retrieves allocations and renders their summary cards
async function loadAllocations() {
    clearErrors();
    statusElement.textContent = "Loading weekly allocations...";
    allocationsContainer.innerHTML = "";

    try {
        const response = await fetch("/api/v1/allocations");

        if (!response.ok) {
            const error = await readError(response);
            showError(error.message);
            statusElement.textContent = "";
            return;
        }

        const allocations = await response.json();

        if (allocations.length === 0) {
            statusElement.textContent = "No weekly allocations found.";
            return;
        }

        statusElement.textContent = "";

        allocations.forEach(allocation => {
            allocationsContainer.appendChild(createAllocationElement(allocation));
        });
    } catch (error) {
        showError("Unable to connect to the allocation service.");
        statusElement.textContent = "";
    }
}

// builds the visible details and actions for one allocation
function createAllocationElement(allocation) {
    const wrapper = document.createElement("div");

    const separator = document.createElement("hr");
    wrapper.appendChild(separator);

    const heading = document.createElement("h2");

    // deliberately hide the backend allocation ID
    heading.textContent = allocation.customerName;
    wrapper.appendChild(heading);

    const week = document.createElement("p");
    week.innerHTML = `<strong>Week Start:</strong> ${escapeHtml(allocation.weekStart)}`;
    wrapper.appendChild(week);

    const table = document.createElement("table");
    table.border = "1";

    table.innerHTML = `
        <thead>
            <tr>
                <th>Taro Type</th>
                <th>Quantity</th>
                <th>Price per kg</th>
            </tr>
        </thead>
        <tbody></tbody>
    `;

    const tableBody = table.querySelector("tbody");

    allocation.allocationItems.forEach(item => {
        const row = document.createElement("tr");

        // deliberately show names rather than backend IDs
        row.innerHTML = `
            <td>${escapeHtml(item.taroTypeName)}</td>
            <td>${escapeHtml(String(item.quantity))}</td>
            <td>$${formatPrice(item.pricePerKg)}</td>
        `;

        tableBody.appendChild(row);
    });

    wrapper.appendChild(table);

    const actions = document.createElement("p");

    const editLink = document.createElement("a");
    editLink.href = `/allocation-edit-client.html?id=${allocation.id}`;
    editLink.textContent = "Edit Allocation";
    actions.appendChild(editLink);

    actions.appendChild(document.createTextNode(" "));

    const deleteButton = document.createElement("button");
    deleteButton.type = "button";
    deleteButton.textContent = "Delete Allocation";

    deleteButton.addEventListener("click", () => {
        deleteAllocation(allocation.id, allocation.customerName);
    });

    actions.appendChild(deleteButton);
    wrapper.appendChild(actions);

    return wrapper;
}

// confirms and deletes the selected allocation
async function deleteAllocation(allocationId, customerName) {
    const confirmed = confirm(`Delete the weekly allocation for ${customerName}?`);

    if (!confirmed) {
        return;
    }

    clearErrors();

    try {
        const response = await fetch(`/api/v1/allocations/${allocationId}`, {
            method: "DELETE"
        });

        if (!response.ok) {
            const error = await readError(response);
            showError(error.message);
            return;
        }

        // reload the allocations after deletion
        await loadAllocations();
    } catch (error) {
        showError("Unable to connect to the allocation service.");
    }
}

// reads a structured error response when available
async function readError(response) {
    try {
        return await response.json();
    } catch (error) {
        return {
            message: "An unexpected error occurred."
        };
    }
}

// displays an error message in the page error list
function showError(message) {
    errorContainer.hidden = false;

    const item = document.createElement("li");
    item.textContent = message;

    errorList.appendChild(item);
}

// removes errors shown by a previous operation
function clearErrors() {
    errorContainer.hidden = true;
    errorList.innerHTML = "";
}

// formats a price with two decimal places
function formatPrice(price) {
    return Number(price).toFixed(2);
}

// escapes text before rendering it in generated html
function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
