package com.example.PayEasy.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.PayEasy.Repository.AccountRepository;
import com.example.PayEasy.Repository.TransactionRepository;
import com.example.PayEasy.Service.MeshSimulatorService;

@Controller
public class ProjectController {

    @Autowired
    private AccountRepository accounts;
    @Autowired
    private TransactionRepository transactions;
    @Autowired
    private MeshSimulatorService mesh;

    @GetMapping("/")
    public String projectPage(Model model) {
        model.addAttribute("accounts", accounts.findAll());
        model.addAttribute("recentTransactions", transactions.findTop20ByOrderByIdDesc());
        model.addAttribute("deviceCounts", mesh.snapshotMap());
        return "index";
    }
}
