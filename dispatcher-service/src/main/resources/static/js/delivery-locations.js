(async function showDeliveryMap() {
    const element = document.getElementById("deliveryMap");
    const status = document.getElementById("deliveryMapStatus");
    if (!element || !status) return;

    const driver = [...document.querySelectorAll(".delivery-point")].map(node => ({
        lat: Number(node.dataset.latitude),
        lng: Number(node.dataset.longitude),
        updatedAt: node.dataset.updatedAt
    })).find(point => Number.isFinite(point.lat) && Number.isFinite(point.lng));
    const destinationAddress = element.dataset.address;
    let apiKey;
    try {
        const response = await fetch("/api/v1/google-places/config");
        if (!response.ok) throw new Error("Configuration unavailable");
        ({apiKey} = await response.json());
    } catch (_) {
        status.textContent = "The map is unavailable. Try again later.";
        return;
    }
    if (!apiKey) {
        status.textContent = "Configure GOOGLE_MAPS_BROWSER_API_KEY to display the map.";
        return;
    }

    window.taroDeliveryMapReady = async () => {
        try {
            const {Map, InfoWindow} = await google.maps.importLibrary("maps");
            const {Geocoder} = await google.maps.importLibrary("geocoding");
            const map = new Map(element, {
                center: {lat: -36.8485, lng: 174.7633}, zoom: 11,
                mapTypeControl: false, streetViewControl: false
            });
            const geocoder = new Geocoder();
            const info = new InfoWindow();
            const colors = {ORIGIN: "#64748b", DESTINATION: "#16a34a"};
            const coveredColor = getComputedStyle(document.documentElement)
                .getPropertyValue("--accent").trim() || "#17665f";
            const remainingColor = "#4a927e";

            map.data.setStyle(feature => {
                const kind = feature.getProperty("kind");
                const vehicle = kind === "DRIVER" || kind === "SIMULATED";
                return {
                    icon: vehicle ? {
                        url: kind === "DRIVER" ? "/images/delivery-driver-marker.svg"
                            : "/images/delivery-driver-illustrative-marker.svg",
                        scaledSize: new google.maps.Size(72, 84),
                        anchor: new google.maps.Point(36, 80)
                    } : {
                        path: google.maps.SymbolPath.CIRCLE,
                        scale: 11,
                        fillColor: colors[kind], fillOpacity: 1,
                        strokeColor: "#ffffff", strokeWeight: 3
                    },
                    title: feature.getProperty("label"), zIndex: vehicle ? 20 : 10
                };
            });
            map.data.addListener("click", event => {
                const content = document.createElement("div");
                const heading = document.createElement("strong");
                heading.textContent = event.feature.getProperty("label");
                content.append(heading);
                const updatedAt = event.feature.getProperty("updatedAt");
                if (updatedAt) {
                    const time = document.createElement("div");
                    time.textContent = `Shared ${updatedAt}`;
                    content.append(time);
                }
                info.setContent(content);
                info.setPosition(event.latLng);
                info.open(map);
            });

            function addPoint(kind, position, label, updatedAt, bounds) {
                map.data.add({geometry: new google.maps.Data.Point(position), properties: {
                    kind, label, updatedAt: updatedAt || ""
                }});
                bounds.extend(position);
            }

            function drawRouteSegment(path, covered, illustrative = false) {
                if (!path || path.length < 2) return;
                new google.maps.Polyline({path, map, strokeColor: "#ffffff",
                    strokeOpacity: 0.96, strokeWeight: covered ? 12 : 10, zIndex: 1});
                const options = {path, map, strokeColor: covered ? coveredColor : remainingColor,
                    strokeOpacity: illustrative ? 0 : 1, strokeWeight: covered ? 8 : 6, zIndex: 2};
                if (illustrative) options.icons = [{icon: {
                    path: google.maps.SymbolPath.CIRCLE,
                    fillColor: coveredColor, fillOpacity: 1,
                    strokeColor: coveredColor, strokeOpacity: 1, scale: 3
                }, offset: "0", repeat: "13px"}];
                new google.maps.Polyline(options);
            }

            function splitRouteHalfway(path) {
                if (path.length < 2) return null;
                const coords = path.map(point => ({lat: point.lat(), lng: point.lng()}));
                const lengths = coords.slice(1).map((point, index) => {
                    const before = coords[index];
                    const north = (point.lat - before.lat) * 111_000;
                    const east = (point.lng - before.lng) * 111_000 *
                        Math.cos(((point.lat + before.lat) / 2) * Math.PI / 180);
                    return Math.hypot(north, east);
                });
                const halfway = lengths.reduce((sum, length) => sum + length, 0) / 2;
                let travelled = 0;
                for (let index = 0; index < lengths.length; index++) {
                    const length = lengths[index];
                    if (travelled + length >= halfway) {
                        const fraction = length ? (halfway - travelled) / length : 0;
                        const before = coords[index];
                        const after = coords[index + 1];
                        const middle = {lat: before.lat + (after.lat - before.lat) * fraction,
                            lng: before.lng + (after.lng - before.lng) * fraction};
                        return {middle,
                            covered: [...path.slice(0, index + 1), middle],
                            remaining: [middle, ...path.slice(index + 1)]};
                    }
                    travelled += length;
                }
                return null;
            }

            async function geocode(address) {
                if (!address) return null;
                try {
                    const result = await geocoder.geocode({address, region: "nz"});
                    return result.results[0]?.geometry.location || null;
                } catch (_) { return null; }
            }

            async function render() {
                const startAddress = element.dataset.startAddress;
                status.textContent = "Loading route...";

                const [origin, destination] = await Promise.all([
                    geocode(startAddress), geocode(destinationAddress)
                ]);
                const bounds = new google.maps.LatLngBounds();
                if (origin) addPoint("ORIGIN", origin, "Starting point: " + startAddress, null, bounds);
                if (driver) {
                    addPoint("DRIVER", {lat: driver.lat, lng: driver.lng},
                        "Delivery driver - shared position", driver.updatedAt, bounds);
                }
                if (destination) addPoint("DESTINATION", destination,
                    "Delivery address: " + destinationAddress, null, bounds);
                if (!startAddress || !destinationAddress) {
                    status.textContent = "A starting address and delivery address are needed to draw a route.";
                    return;
                }
                try {
                    const {Route} = await google.maps.importLibrary("routes");
                    const request = {
                        origin: startAddress, destination: destinationAddress,
                        travelMode: "DRIVING", fields: ["path", "legs"]
                    };
                    if (driver) request.intermediates = [{location: {lat: driver.lat, lng: driver.lng}}];
                    const {routes} = await Route.computeRoutes(request);
                    if (!routes?.length) throw new Error("No road route found");
                    const roadLines = routes[0].createPolylines();
                    roadLines.forEach(line => {
                        line.getPath().forEach(point => bounds.extend(point));
                    });
                    const path = roadLines[0]?.getPath().getArray() || [];
                    if (driver && routes[0].legs?.length >= 2) {
                        drawRouteSegment(routes[0].legs[0].path, true);
                        drawRouteSegment(routes[0].legs[1].path, false);
                    } else if (path.length >= 2) {
                        const split = splitRouteHalfway(path);
                        drawRouteSegment(split.covered, true, !driver);
                        drawRouteSegment(split.remaining, false);
                    }
                    if (!driver) {
                        const split = splitRouteHalfway(path);
                        if (split) addPoint("SIMULATED", split.middle,
                            "Estimated delivery driver position - no update shared", null, bounds);
                    }
                    status.hidden = true;
                } catch (_) {
                    if (origin && destination) {
                        const middle = driver ? {lat: driver.lat, lng: driver.lng} : {
                            lat: (origin.lat() + destination.lat()) / 2,
                            lng: (origin.lng() + destination.lng()) / 2
                        };
                        if (!driver) addPoint("SIMULATED", middle,
                            "Estimated delivery driver position - no update shared", null, bounds);
                        drawRouteSegment([origin, middle], true, !driver);
                        drawRouteSegment([middle, destination], false);
                    }
                    status.textContent = "Road directions are unavailable. The green segments are direct connections, not a driving route." +
                        (driver ? " Dark green ends at the last shared driver position." : " Dotted green shows estimated progress.");
                }
                if (!bounds.isEmpty()) {
                    map.fitBounds(bounds, 55);
                    google.maps.event.addListenerOnce(map, "idle", () => {
                        if (map.getZoom() > 14) map.setZoom(14);
                    });
                }
            }

            await render();
        } catch (_) {
            status.textContent = "The map could not load. Try again later.";
        }
    };

    const script = document.createElement("script");
    script.async = true;
    script.src = "https://maps.googleapis.com/maps/api/js?key=" +
        encodeURIComponent(apiKey) + "&v=weekly&loading=async&callback=taroDeliveryMapReady";
    script.onerror = () => { status.textContent = "The map could not load. Try again later."; };
    document.head.appendChild(script);
})();
