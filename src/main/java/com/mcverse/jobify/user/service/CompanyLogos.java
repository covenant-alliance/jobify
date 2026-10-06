package com.mcverse.jobify.user.service;

import com.mcverse.jobify.user.model.Company;

/** The public address of a company's logo. */
public final class CompanyLogos {

    private CompanyLogos() {}

    /** Relative to the API address; the version makes the address change when the logo does. Null without a logo. */
    public static String urlOf(Company company) {
        if (company == null || company.getLogoPath() == null) return null;
        return "/companies/" + company.getId() + "/logo?v=" + company.getLogoVersion();
    }
}
