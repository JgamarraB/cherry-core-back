package com.cherrycore.company.application;

import com.cherrycore.company.domain.MembershipStatus;

public record MyCompanyView(CompanyView company, String roleCode, MembershipStatus membershipStatus) {
}
