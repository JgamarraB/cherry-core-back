package com.cherrycore.company.application;

import com.cherrycore.company.domain.MembershipStatus;

/**
 * @param roleName nombre visible del rol (los roles propios de cada empresa no tienen traducción)
 */
public record MyCompanyView(CompanyView company, String roleCode, String roleName,
        MembershipStatus membershipStatus) {
}
