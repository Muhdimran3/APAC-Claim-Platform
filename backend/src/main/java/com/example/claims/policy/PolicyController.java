package com.example.claims.policy;

import com.example.claims.user.CurrentUser;
import com.example.claims.user.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyRepository policies;

    public PolicyController(PolicyRepository policies) {
        this.policies = policies;
    }

    /** Claimants see their own policies (to pick one when reporting an incident); staff see all. */
    @GetMapping
    public List<PolicyResponse> list(CurrentUser user) {
        List<Policy> result = user.is(Role.CLAIMANT)
                ? policies.findByHolderIdOrderByPolicyNumber(user.id())
                : policies.findAllByOrderByPolicyNumber();
        return result.stream().map(PolicyResponse::from).toList();
    }

    public record PolicyResponse(UUID id, String policyNumber, Market market, Product product, String currency,
                                 BigDecimal sumInsured, LocalDate effectiveFrom, LocalDate effectiveTo) {
        static PolicyResponse from(Policy p) {
            return new PolicyResponse(p.getId(), p.getPolicyNumber(), p.getMarket(), p.getProduct(),
                    p.getCurrency(), p.getSumInsured(), p.getEffectiveFrom(), p.getEffectiveTo());
        }
    }
}
