package lt.satsyuk.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lt.satsyuk.dto.AccountResponse;
import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.dto.UpdateBalanceRequest;
import lt.satsyuk.service.AccountService;
import lt.satsyuk.service.SecurityService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Account management")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;
    private final SecurityService securityService;

    public AccountController(AccountService accountService, SecurityService securityService) {
        this.accountService = accountService;
        this.securityService = securityService;
    }

    @PostMapping("/balance/pessimistic")
    @PreAuthorize("hasRole('UPDATE_BALANCE')")
    @Operation(summary = "Update balance with pessimistic lock", description = "Updates account balance using pessimistic write lock.")
    @ApiResponse(responseCode = "200", description = "Balance updated",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AppResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "403", description = "Forbidden",
            content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "409", description = "Idempotency key conflict, request already in progress, or optimistic lock conflict",
            content = @Content(mediaType = "application/json"))
    public AppResponse<AccountResponse> updateBalancePessimistic(@Valid @RequestBody UpdateBalanceRequest request) {
        return AppResponse.ok(accountService.updateBalancePessimistic(request));
    }

    @PostMapping("/balance/optimistic")
    @PreAuthorize("hasRole('UPDATE_BALANCE')")
    @Operation(summary = "Update balance with optimistic lock", description = "Updates account balance using optimistic locking with automatic retries.")
    @ApiResponse(responseCode = "200", description = "Balance updated",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AppResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "403", description = "Forbidden",
            content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "409", description = "Idempotency key conflict or optimistic lock conflict",
            content = @Content(mediaType = "application/json"))
    public AppResponse<AccountResponse> updateBalanceOptimistic(@Valid @RequestBody UpdateBalanceRequest request) {
        return AppResponse.ok(accountService.updateBalanceOptimistic(request));
    }

    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasRole('CLIENT_GET')")
    @Operation(summary = "Get account balance by client id", description = "Returns account information for a client.")
    @ApiResponse(responseCode = "200", description = "Account found",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AppResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "403", description = "Forbidden",
            content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(mediaType = "application/json"))
    public AppResponse<AccountResponse> getByClientId(@PathVariable("clientId") Long clientId) {
        return AppResponse.ok(accountService.getByClientId(clientId, securityService.clientId()));
    }
}
