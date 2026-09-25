// loads and updates the taro type selected by the url id
const params = new URLSearchParams(window.location.search);
const taroTypeId = params.get("id");

const form = document.getElementById("taroTypeForm");

// loads the selected taro type's values into the form
async function loadTaroType() {

    if (!taroTypeId) {
        showError("Taro type ID is missing.");
        return;
    }

    try {
        const response = await fetch(
            `/api/v1/taro-types/${taroTypeId}`
        );

        if (!response.ok) {
            const error = await response.json();
            showError(error.message);
            return;
        }

        const taroType = await response.json();

        document.getElementById("name").value =
            taroType.name ?? "";

        document.getElementById("description").value =
            taroType.description ?? "";

        document.getElementById("standardPrice").value =
            taroType.standardPrice ?? "";

    } catch (error) {
        showError("Unable to communicate with the server.");
    }
}

// saves the edited taro type details
form.addEventListener("submit", async function (event) {

    event.preventDefault();

    clearErrors();

    const taroType = {
        name: document.getElementById("name").value,
        description: document.getElementById("description").value,
        standardPrice:
            document.getElementById("standardPrice").value
    };

    try {
        const response = await fetch(
            `/api/v1/taro-types/${taroTypeId}`,
            {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(taroType)
            }
        );

        if (!response.ok) {
            const error = await response.json();
            showError(error.message);
            return;
        }

        window.location.href =
            "/taro-types-client.html";

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

// loads the selected taro type when the page opens
loadTaroType();
