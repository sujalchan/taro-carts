// manages taro type search results and table rendering
const status = document.getElementById("status");
const table = document.getElementById("taroTypeTable");
const tableBody = document.getElementById("taroTypeTableBody");
const searchForm = document.getElementById("searchForm");
const searchInput = document.getElementById("search");
const clearSearchButton = document.getElementById("clearSearch");

// loads taro types, optionally filtered by the search term
async function loadTaroTypes(search = "") {
    status.textContent = "Loading taro types...";
    table.hidden = true;

    try {
        let url = "/api/v1/taro-types";

        if (search.trim() !== "") {
            url += `?search=${encodeURIComponent(search.trim())}`;
        }

        const response = await fetch(url);

        if (!response.ok) {
            throw new Error(
                "Unable to load taro types. HTTP " + response.status
            );
        }

        const taroTypes = await response.json();

        // clear any existing rows
        tableBody.innerHTML = "";

        if (taroTypes.length === 0) {
            status.textContent = "No taro types found.";
            table.hidden = true;
            return;
        }

        taroTypes.forEach(taroType => {
            const row = document.createElement("tr");
            const price = Number(taroType.standardPrice).toFixed(2);

            row.innerHTML = `
                <td>${taroType.id}</td>
                <td>${escapeHtml(taroType.name)}</td>
                <td>${escapeHtml(taroType.description ?? "")}</td>
                <td>$${price}</td>
                <td>
                    <a href="/taro-type-edit-client.html?id=${taroType.id}">
                        Edit
                    </a>
                </td>
            `;

            tableBody.appendChild(row);
        });

        status.textContent = "";
        table.hidden = false;
    } catch (error) {
        table.hidden = true;
        status.textContent = error.message;
    }
}

// loads results for the submitted search term
searchForm.addEventListener("submit", event => {
    event.preventDefault();
    loadTaroTypes(searchInput.value);
});

// clears the search term and reloads all taro types
clearSearchButton.addEventListener("click", () => {
    searchInput.value = "";
    loadTaroTypes();
});

// escapes text before rendering it in the table
function escapeHtml(value) {
    const element = document.createElement("div");
    element.textContent = value;
    return element.innerHTML;
}

// loads the initial taro type list
loadTaroTypes();
