package sakila.demo.controller;

import sakila.demo.entity.Rental;
import sakila.demo.metrics.BusinessMetrics;
import sakila.demo.repository.PaymentRepository;
import sakila.demo.repository.RentalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class RentalController {

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private BusinessMetrics businessMetrics;

    @GetMapping("/rental-list")
    public String getRentalsBetweenDates(@RequestParam String startDate, @RequestParam String endDate, Model model) {
        return businessMetrics.track("rental.list", () -> loadRentals(startDate, endDate, model));
    }

    private String loadRentals(String startDate, String endDate, Model model) {
        LocalDate start = LocalDate.parse(startDate, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        LocalDate end = LocalDate.parse(endDate, DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        List<Rental> rentals = rentalRepository.findByRentalDateBetween(start, end);
        model.addAttribute("rentals", rentals);
        businessMetrics.recordVolume("rental.list", rentals.size());

        // Assuming Payment entity and PaymentRepository are also created
        Double totalPayment = paymentRepository.findTotalPaymentBetweenDates(start, end);
        model.addAttribute("totalPayment", totalPayment);

        return "/sakila/rentals";
    }
}
