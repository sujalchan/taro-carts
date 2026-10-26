document.addEventListener("DOMContentLoaded", () => {
    const customerDetails = document.getElementById("customer-details");
    const otherAccountDetails = document.getElementById("other-account-details");
    const businessName = document.getElementById("businessName");
    const name = document.getElementById("name");
    const accountTypeOptions = document.querySelectorAll('input[name="accountType"]');
    const customerFields = customerDetails.querySelectorAll("input");

    function updateCustomerFields() {
        const selectedType = document.querySelector('input[name="accountType"]:checked')?.value;
        const isCustomer = selectedType === "CUSTOMER";
        const hasAccountType = selectedType !== undefined;
        customerDetails.hidden = !isCustomer;
        otherAccountDetails.hidden = !hasAccountType || isCustomer;
        businessName.required = isCustomer;
        name.required = hasAccountType && !isCustomer;
        customerFields.forEach((field) => {
            field.disabled = !isCustomer;
        });
        name.disabled = !hasAccountType || isCustomer;
    }

    accountTypeOptions.forEach((option) => option.addEventListener("change", updateCustomerFields));
    updateCustomerFields();
});
