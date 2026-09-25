// manages customer search results and table rendering
const statusElement = document.getElementById("status");
const table = document.getElementById("customerTable");
const tableBody = document.getElementById("customerTableBody");
const searchForm = document.getElementById("searchForm");
const searchInput = document.getElementById("search");
const clearSearchButton = document.getElementById("clearSearch");

// loads customers, optionally filtered by the search term
async function loadCustomers(search = "") {
    statusElement.textContent = "Loading customers...";
    table.hidden = true;

    try {
        let url = "/api/v1/customers";

        if (search.trim() !== "") {
            url += `?search=${encodeURIComponent(search.trim())}`;
        }

        const response = await fetch(url);

        if (!response.ok) {
            throw new Error(
                "Unable to load customers. HTTP " + response.status
            );
        }

        const customers = await response.json();

        // clear any existing rows
        tableBody.innerHTML = "";

        if (customers.length === 0) {
            statusElement.textContent = "No customers found.";
            table.hidden = true;
            return;
        }

        customers.forEach(customer => {
            const row = document.createElement("tr");

            row.innerHTML = `
                <td>${escapeHtml(customer.name)}</td>
                <td>${escapeHtml(customer.contactName ?? "")}</td>
                <td>${escapeHtml(customer.phone ?? "")}</td>
                <td>${customer.active ? "Yes" : "No"}</td>
                <td>
                    <a href="/customer-edit-client.html?id=${customer.id}">
                        Edit
                    </a>
                </td>
            `;

            tableBody.appendChild(row);
        });

        statusElement.textContent = "";
        table.hidden = false;
    } catch (error) {
        table.hidden = true;
        statusElement.textContent = error.message;
    }
}

// loads results for the submitted search term
searchForm.addEventListener("submit", event => {
    event.preventDefault();
    loadCustomers(searchInput.value);
});

// clears the search term and reloads all customers
clearSearchButton.addEventListener("click", () => {
    searchInput.value = "";
    loadCustomers();
});

// escapes text before rendering it in the table
function escapeHtml(value) {
    const element = document.createElement("div");
    element.textContent = value;
    return element.innerHTML;
}

// loads the initial customer list
loadCustomers();
