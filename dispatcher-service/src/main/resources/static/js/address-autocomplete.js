// One editable address field with optional Google Places suggestions.
(async function initAddressAutocomplete() {
    const address = document.getElementById("address");
    const container = document.getElementById("addressAutocomplete");
    const suggestionsList = document.getElementById("addressSuggestions");
    const status = document.getElementById("addressAutocompleteStatus");
    if (!address || !container || !suggestionsList || !status) return;

    let apiKey;
    try {
        const response = await fetch("/api/v1/google-places/config");
        if (!response.ok) return;
        ({ apiKey } = await response.json());
    } catch (_) {
        return;
    }
    if (!apiKey) return;

    window.taroPlacesReady = async () => {
        try {
            const { AutocompleteSessionToken, AutocompleteSuggestion } =
                await google.maps.importLibrary("places");
            let sessionToken = null;
            let debounceTimer;
            let requestVersion = 0;

            function closeSuggestions() {
                container.hidden = true;
                suggestionsList.replaceChildren();
                address.setAttribute("aria-expanded", "false");
            }

            async function selectSuggestion(prediction) {
                const version = ++requestVersion;
                sessionToken = null;
                clearTimeout(debounceTimer);
                closeSuggestions();
                status.textContent = "";
                address.focus();
                try {
                    const place = prediction.toPlace();
                    await place.fetchFields({ fields: ["formattedAddress"] });
                    if (version !== requestVersion) return;
                    if (!place.formattedAddress || place.formattedAddress.length > address.maxLength) {
                        status.textContent = "Enter this address manually in the address field.";
                        return;
                    }
                    address.value = place.formattedAddress;
                } catch (_) {
                    if (version === requestVersion) {
                        status.textContent = "Could not load that address. Enter it manually.";
                    }
                }
            }

            function showSuggestions(suggestions) {
                suggestionsList.replaceChildren();
                for (const suggestion of suggestions) {
                    const prediction = suggestion.placePrediction;
                    if (!prediction) continue;
                    const option = document.createElement("button");
                    option.type = "button";
                    option.className = "address-suggestion";
                    option.setAttribute("role", "option");
                    option.textContent = prediction.text.toString();
                    option.addEventListener("click", () => selectSuggestion(prediction));
                    suggestionsList.appendChild(option);
                }
                container.hidden = !suggestionsList.childElementCount;
                address.setAttribute("aria-expanded", String(!container.hidden));
            }

            address.addEventListener("input", () => {
                const version = ++requestVersion;
                clearTimeout(debounceTimer);
                closeSuggestions();
                status.textContent = "";
                const query = address.value.trim();
                if (query.length < 3) {
                    sessionToken = null;
                    return;
                }
                if (!sessionToken) sessionToken = new AutocompleteSessionToken();
                debounceTimer = setTimeout(async () => {
                    try {
                        const { suggestions } = await AutocompleteSuggestion.fetchAutocompleteSuggestions({
                            input: query,
                            includedRegionCodes: ["nz"],
                            region: "nz",
                            sessionToken
                        });
                        if (version === requestVersion && document.activeElement === address) {
                            showSuggestions(suggestions);
                        }
                    } catch (_) {
                        if (version === requestVersion) {
                            status.textContent = "Address suggestions are unavailable. Enter the address manually.";
                        }
                    }
                }, 300);
            });

            address.addEventListener("keydown", (event) => {
                if (event.key === "Escape") closeSuggestions();
                if (event.key === "Enter" && !container.hidden) {
                    event.preventDefault();
                    suggestionsList.firstElementChild?.click();
                }
                if (event.key === "ArrowDown" && !container.hidden) {
                    event.preventDefault();
                    suggestionsList.firstElementChild?.focus();
                }
            });

            suggestionsList.addEventListener("keydown", (event) => {
                const options = Array.from(suggestionsList.children);
                const index = options.indexOf(document.activeElement);
                if (event.key === "Escape") {
                    closeSuggestions();
                    address.focus();
                } else if (event.key === "ArrowDown" || event.key === "ArrowUp") {
                    event.preventDefault();
                    const next = event.key === "ArrowDown" ? index + 1 : index - 1;
                    (options[next] || (next < 0 ? address : options[0]))?.focus();
                }
            });

            const field = address.closest(".address-field");
            field.addEventListener("focusout", () => {
                setTimeout(() => {
                    if (!field.contains(document.activeElement)) {
                        closeSuggestions();
                        sessionToken = null;
                    }
                }, 0);
            });
        } catch (_) {
            status.textContent = "Address suggestions are unavailable. Enter the address manually.";
        }
    };

    const script = document.createElement("script");
    script.async = true;
    script.src = "https://maps.googleapis.com/maps/api/js?key=" +
        encodeURIComponent(apiKey) + "&v=weekly&loading=async&callback=taroPlacesReady";
    script.onerror = () => {
        status.textContent = "Address suggestions are unavailable. Enter the address manually.";
    };
    document.head.appendChild(script);
})();
