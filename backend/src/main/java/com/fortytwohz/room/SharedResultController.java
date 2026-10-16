
package com.fortytwohz.room;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SharedResultController {

    private final SharedResultService service;

    public SharedResultController(SharedResultService service) {
        this.service = service;
    }

    @ModelAttribute
    public void disableCaching(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @DeleteMapping("/rooms/{code}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeShare(@PathVariable String code,
            @RequestHeader(value = "Authorization", required = false) String bearer) {
        service.revokeShare(code, bearer);
    }

    @PostMapping("/rooms/{code}/share")
    @ResponseStatus(HttpStatus.CREATED)
    public SharedResultService.ShareResponse createShare(
            @PathVariable String code,
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String bearer
    ) {
        return service.createShare(code, bearer);
    }

    @GetMapping("/shared-results/{token}")
    public SharedResultService.PublicResult getSharedResult(
            @PathVariable String token
    ) {
        return service.getSharedResult(token);
    }
}
