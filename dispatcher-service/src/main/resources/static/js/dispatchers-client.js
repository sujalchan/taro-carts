const statusElement = document.getElementById("status");
const table = document.getElementById("dispatcherTable");
const tableBody = document.getElementById("dispatcherTableBody");
const searchForm = document.getElementById("searchForm");
const searchInput = document.getElementById("search");
const clearSearchButton = document.getElementById("clearSearch");

async function loadDispatchers(search = "") {
    statusElement.textContent = "Loading dispatchers...";
    table.hidden = true;
    try {
        const query = search.trim();
        const url = query ? `/api/v1/dispatchers?search=${encodeURIComponent(query)}` : "/api/v1/dispatchers";
        const response = await fetch(url);
        if (!response.ok) throw new Error(`Unable to load dispatchers. HTTP ${response.status}`);
        const dispatchers = await response.json();
        tableBody.replaceChildren();

        if (dispatchers.length === 0) {
            statusElement.textContent = "No dispatchers found.";
            return;
        }

        for (const dispatcher of dispatchers) {
            const row = document.createElement("tr");
            for (const value of [
                dispatcher.name,
                dispatcher.address,
                dispatcher.contactName,
                dispatcher.phone,
                dispatcher.active ? "Yes" : "No"
            ]) {
                const cell = document.createElement("td");
                cell.textContent = value ?? "";
                row.appendChild(cell);
            }
            const actions = document.createElement("td");
            const editLink = document.createElement("a");
            editLink.href = `/dispatcher-edit-client.html?id=${encodeURIComponent(dispatcher.id)}`;
            editLink.textContent = "Edit";
            actions.appendChild(editLink);
            row.appendChild(actions);
            tableBody.appendChild(row);
        }
        statusElement.textContent = "";
        table.hidden = false;
    } catch (error) {
        statusElement.textContent = error.message;
    }
}

searchForm.addEventListener("submit", event => {
    event.preventDefault();
    loadDispatchers(searchInput.value);
});

clearSearchButton.addEventListener("click", () => {
    searchInput.value = "";
    loadDispatchers();
});

loadDispatchers();
