// Guides registration through account details and role details after role selection.
(() => {
    const form = document.getElementById("registration-form");
    if (!form) return;

    const accountStep = form.querySelector('[data-wizard-step="2"]');
    const profileStep = form.querySelector('[data-wizard-step="3"]');
    const continueButton = document.getElementById("continue-to-profile");
    const backButton = document.getElementById("back-to-account");
    const stepLabel = document.getElementById("wizard-step-label");
    const stepIntro = document.getElementById("wizard-step-intro");
    const title = document.getElementById("register-title");
    const password = document.getElementById("password");
    const confirmation = document.getElementById("confirmPassword");
    const profileIntro = document.querySelector(".step-help").textContent;
    let currentStep = 2;

    function showStep(step, focusTitle = false) {
        currentStep = step;
        accountStep.hidden = step !== 2;
        profileStep.hidden = step !== 3;
        // Hidden required fields must not block Enter on the visible step.
        profileStep.disabled = step !== 3;
        accountStep.classList.toggle("is-active", step === 2);
        profileStep.classList.toggle("is-active", step === 3);
        for (const indicator of document.querySelectorAll("[data-step-indicator]")) {
            if (Number(indicator.dataset.stepIndicator) === step) {
                indicator.setAttribute("aria-current", "step");
            } else {
                indicator.removeAttribute("aria-current");
            }
        }
        stepLabel.textContent = `Step ${step} of 3`;
        stepIntro.textContent = step === 2
            ? "Choose the details you’ll use to sign in after approval."
            : profileIntro;
        if (focusTitle) {
            title.setAttribute("tabindex", "-1");
            title.focus({ preventScroll: true });
        }
    }

    function continueToProfile() {
        confirmation.setCustomValidity("");
        for (const input of accountStep.querySelectorAll("input")) {
            if (!input.reportValidity()) return;
        }
        if (password.value !== confirmation.value) {
            confirmation.setCustomValidity("Passwords do not match.");
            confirmation.reportValidity();
            return;
        }
        showStep(3, true);
    }

    confirmation.addEventListener("input", () => confirmation.setCustomValidity(""));
    continueButton.addEventListener("click", continueToProfile);
    backButton.addEventListener("click", () => showStep(2, true));
    form.addEventListener("submit", event => {
        if (currentStep === 2) {
            event.preventDefault();
            continueToProfile();
        }
    });

    showStep(2);
})();
