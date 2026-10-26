// One editable address field with optional Google Places suggestions.
(async function initAddressAutocomplete() {
    const address = document.getElementById("address");
    const container = document.getElementById("addressAutocomplete");
    const suggestionsList = document.getElementById("addressSuggestions");
    const status = document.getElementById("addressAutocompleteStatus");
    if (!address || !container || !suggestionsList || !status) return;

    const form = address.form;
    const addressField = address.closest(".address-field");
    const editingExistingAddress = location.pathname.includes("edit");
    let hasVerifiedAddress = editingExistingAddress && address.value.trim().length > 0;

    address.addEventListener("input", () => {
        hasVerifiedAddress = false;
        address.setCustomValidity("");
    });

    form?.addEventListener("submit", event => {
        if (hasVerifiedAddress) return;
        event.preventDefault();
        event.stopImmediatePropagation();
        address.setCustomValidity("Select a complete address from the Google Places suggestions.");
        address.reportValidity();
        status.textContent = "Select a complete street address from the suggestions before submitting.";
        address.focus();
    }, true);

    let apiKey;
    try {
        const response = await fetch("/api/v1/google-places/config");
        if (!response.ok) {
            status.textContent = "Google Places address checking is unavailable. Try again later.";
            return;
        }
        ({ apiKey } = await response.json());
    } catch (_) {
        status.textContent = "Google Places address checking is unavailable. Try again later.";
        return;
    }
    if (!apiKey) {
        status.textContent = "Google Places is not configured, so an address cannot be checked.";
        return;
    }

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
                    await place.fetchFields({ fields: ["formattedAddress", "addressComponents"] });
                    if (version !== requestVersion) return;
                    if (!place.formattedAddress || place.formattedAddress.length > address.maxLength) {
                        status.textContent = "Choose a complete street address from the suggestions.";
                        return;
                    }
                    const components = place.addressComponents || [];
                    const types = new Set(components.flatMap(component => component.types || []));
                    const country = components.find(component => component.types?.includes("country"));
                    const hasLocality = ["locality", "postal_town", "sublocality_level_1", "postal_code"]
                        .some(type => types.has(type));
                    if (!types.has("street_number") || !types.has("route") || !hasLocality
                            || country?.shortText !== "NZ") {
                        hasVerifiedAddress = false;
                        status.textContent = "Select a complete New Zealand street address, including its street number.";
                        return;
                    }
                    address.value = place.formattedAddress;
                    hasVerifiedAddress = true;
                    address.setCustomValidity("");
                } catch (_) {
                    if (version === requestVersion) {
                        status.textContent = "Could not check that address. Select a suggestion and try again.";
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
                            status.textContent = "Address suggestions are unavailable. Try again later.";
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

            addressField?.addEventListener("focusout", () => {
                setTimeout(() => {
                    if (!field.contains(document.activeElement)) {
                        closeSuggestions();
                        sessionToken = null;
                    }
                }, 0);
            });
        } catch (_) {
            status.textContent = "Address suggestions are unavailable. Try again later.";
        }
    };

    const script = document.createElement("script");
    script.async = true;
    script.src = "https://maps.googleapis.com/maps/api/js?key=" +
        encodeURIComponent(apiKey) + "&v=weekly&loading=async&callback=taroPlacesReady";
    script.onerror = () => {
        status.textContent = "Address suggestions are unavailable. Try again later.";
    };
    document.head.appendChild(script);
})();
