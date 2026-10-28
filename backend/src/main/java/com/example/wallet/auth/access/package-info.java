/**
 * The RBAC matrix from the spec, as one annotation per capability:
 *
 * <pre>
 * Capability                         CUSTOMER  SUPPORT  ADMIN   Annotation
 * Own wallets: create/view              Y         -       Y     {@link CanHoldWallets}
 * Transfer/deposit from own wallet      Y         N       N     {@link CanMoveMoney}
 * View any user's data                  N       Y(ro)     Y     {@link CanViewAnyUser}
 * Reverse / freeze / run reconciliation N         N       Y     {@link AdminOnly}
 * View reconciliation                   N         Y       Y     {@link CanViewAnyUser}
 * </pre>
 *
 * WHY capability names instead of raw {@code @PreAuthorize("hasRole(...)")}: the rule is
 * written once, a typo can't silently open an endpoint, and reading a controller tells you
 * which row of the matrix applies. There is no role hierarchy: ADMIN is not a superset of
 * CUSTOMER (an admin may not move customer money).
 *
 * <p>Roles answer "may this kind of user call this endpoint?". Ownership ("is this MY
 * wallet?") is a separate check in the service layer, see ADR-009.
 */
package com.example.wallet.auth.access;
