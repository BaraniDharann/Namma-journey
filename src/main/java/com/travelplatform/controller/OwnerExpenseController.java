package com.travelplatform.controller;

import com.travelplatform.service.ExpenseService;
import com.travelplatform.service.ExpenseService.BusToday;
import com.travelplatform.service.ExpenseService.ExpenseForm;
import com.travelplatform.service.ExpenseService.ExpenseView;
import com.travelplatform.service.ExpenseService.ProfitSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * An owner's running costs per car and bus (V33), the profit per vehicle, and the bus dashboard's
 * day view. Everything acts on the calling owner's own vehicles only.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerExpenseController {

    private final ExpenseService expenseService;

    /** The expense log, newest first; {@code kind} = CAR or BUS narrows it. Default: this month. */
    @GetMapping("/api/owner/expenses")
    public ResponseEntity<List<ExpenseView>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String kind) {
        return ResponseEntity.ok(expenseService.list(from, to, kind));
    }

    /** JSON {vehicleKind, vehicleId, busTripId?, spentOn?, category, amount, note?}. */
    @PostMapping("/api/owner/expenses")
    public ResponseEntity<ExpenseView> add(@RequestBody ExpenseForm form) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.add(form));
    }

    @DeleteMapping("/api/owner/expenses/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Revenue, expenses and profit per car and bus. Default: this month so far. */
    @GetMapping("/api/owner/expenses/summary")
    public ResponseEntity<ProfitSummary> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String kind) {
        return ResponseEntity.ok(expenseService.summary(from, to, kind));
    }

    /** One day's bus trips with seats, booking close time, revenue, expense and profit. Default: today. */
    @GetMapping("/api/owner/bus/day")
    public ResponseEntity<BusToday> busDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(expenseService.busDay(date));
    }
}
