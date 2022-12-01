package com.karlaru.tcw.controllers;

import com.karlaru.tcw.response.models.ApiException;
import com.karlaru.tcw.response.models.AvailableChangeTime;
import com.karlaru.tcw.response.models.Booking;
import com.karlaru.tcw.response.models.ContactInformation;
import com.karlaru.tcw.workshops.Workshop;
import com.karlaru.tcw.workshops.WorkshopInterface;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Predicate;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/workshop")
public class WorkshopController {

    private final List<? extends WorkshopInterface> workshopList;

    @GetMapping
    public Flux<Workshop> getWorkshops(){
        return Flux.fromStream(workshopList.stream())
                .map(WorkshopInterface::getWorkshop)
                .switchIfEmpty(
                        Flux.error(new ApiException(HttpStatus.NOT_FOUND.value(), "Workshop list is empty!")));
    }

    @GetMapping(value = "/{workshop}/tire-change-times")
    public ResponseEntity<Flux<AvailableChangeTime>> getAvailableTimes(@PathVariable List<String> workshop,
                                                                      @RequestParam String from,
                                                                      @RequestParam String until,
                                                                      @RequestParam(required = false, defaultValue = "ALL") String vehicle){

        // Get workshops by workshop name and vehicle type
        List<? extends WorkshopInterface> workshopsToGetTimesFor = workshopList.stream()
                .filter(w -> workshop.contains("All") || workshop.contains(w.getWorkshop().name()))
                .filter(w -> vehicle.equals("ALL") || w.getWorkshop().vehicles().contains(Workshop.VehicleType.valueOf(vehicle)))
                .toList();

        // Vehicle type incompatible with selected workshop
        if (workshopsToGetTimesFor.size() == 0)
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Flux.empty());

        // Get times for 1 workshop
        else if (workshopsToGetTimesFor.size() == 1) {
            Flux<AvailableChangeTime> responseBody = Flux.fromStream(workshopsToGetTimesFor.stream())
                    .flatMap(w -> w.getAvailableChangeTime(from, until)).onErrorMap(Predicate.not(ApiException.class::isInstance),
                            throwable -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), " REST api seems to be offline"));
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(responseBody);
        }

        // Get times for multiple workshops, errors will be silently discarded
        Flux<AvailableChangeTime> responseBody = Flux.fromStream(workshopsToGetTimesFor.stream())
                                                     .flatMap(w -> w.getAvailableChangeTime(from, until).onErrorComplete());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseBody);
    }

    @PostMapping(value = "/{workshop}/tire-change-times/{id}/booking", consumes = "application/json")
    public ResponseEntity<Mono<Booking>> bookAvailableTime(@PathVariable String workshop,
                                                           @PathVariable Object id,
                                                           @RequestBody Mono<ContactInformation> contactInformation){

        WorkshopInterface bookWorkshop = workshopList.stream()
                .filter(w -> w.getWorkshop().name().equals(workshop))
                .findAny()
                .orElse(null);

        if (bookWorkshop == null){
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Mono.empty());
        }
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(bookWorkshop.bookChangeTime(id, contactInformation));

    }
}