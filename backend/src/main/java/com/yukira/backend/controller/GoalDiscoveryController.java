package com.yukira.backend.controller;

import com.yukira.backend.dto.discovery.GoalDiscoveryRequest;
import com.yukira.backend.dto.discovery.GoalDiscoveryResponse;
import com.yukira.backend.service.discovery.GoalDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller exposing REST endpoint for Goal-Based Mutual Fund Discovery.
 *
 * Epistemic Rules:
 * 1. Backend owns all matching and eligibility logic.
 * 2. NO recommendation, BUY/SELL/HOLD advice, or future-return simulation.
 * 3. Returns transparent analytical results, explicit criterion evaluation states,
 *    evidence recency, and investigation questions.
 */
@RestController
@RequestMapping("/api/v1/discovery/goals")
public class GoalDiscoveryController {

    private final GoalDiscoveryService goalDiscoveryService;

    public GoalDiscoveryController(GoalDiscoveryService goalDiscoveryService) {
        this.goalDiscoveryService = goalDiscoveryService;
    }

    /**
     * Evaluates mutual fund scheme options against explicit investor goal requirements.
     */
    @PostMapping("/evaluate")
    public ResponseEntity<GoalDiscoveryResponse> evaluateGoal(@RequestBody GoalDiscoveryRequest request) {
        GoalDiscoveryResponse response = goalDiscoveryService.evaluateGoalDiscovery(request);
        return ResponseEntity.ok(response);
    }
}
