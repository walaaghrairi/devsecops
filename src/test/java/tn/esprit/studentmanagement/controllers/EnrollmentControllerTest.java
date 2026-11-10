package tn.esprit.studentmanagement.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.esprit.studentmanagement.entities.Enrollment;
import tn.esprit.studentmanagement.entities.Status;
import tn.esprit.studentmanagement.services.IEnrollment;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Routing, binding and serialisation for the enrollment endpoints, including the
 * status enum, which is stored by name.
 */
class EnrollmentControllerTest {

    private IEnrollment enrollmentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        enrollmentService = mock(IEnrollment.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EnrollmentController(enrollmentService)).build();
    }

    @Test
    @DisplayName("GET /Enrollment/getAllEnrollment returns the enrollments as JSON")
    void listsEnrollments() throws Exception {
        Enrollment active = new Enrollment();
        active.setIdEnrollment(1L);
        active.setStatus(Status.ACTIVE);
        when(enrollmentService.getAllEnrollments()).thenReturn(List.of(active));

        mockMvc.perform(get("/Enrollment/getAllEnrollment"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /Enrollment/getEnrollment/{id} passes the id to the service")
    void getsEnrollmentById() throws Exception {
        Enrollment completed = new Enrollment();
        completed.setIdEnrollment(4L);
        completed.setStatus(Status.COMPLETED);
        when(enrollmentService.getEnrollmentById(4L)).thenReturn(completed);

        mockMvc.perform(get("/Enrollment/getEnrollment/4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idEnrollment").value(4));
    }

    @Test
    @DisplayName("POST /Enrollment/createEnrollment binds the body and saves it")
    void createsEnrollment() throws Exception {
        when(enrollmentService.saveEnrollment(any(Enrollment.class))).thenAnswer(call -> {
            Enrollment saved = call.getArgument(0);
            saved.setIdEnrollment(21L);
            return saved;
        });

        mockMvc.perform(post("/Enrollment/createEnrollment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DROPPED\",\"grade\":12.5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idEnrollment").value(21))
            .andExpect(jsonPath("$.status").value("DROPPED"));
    }

    @Test
    @DisplayName("DELETE /Enrollment/deleteEnrollment/{id} delegates the id")
    void deletesEnrollment() throws Exception {
        mockMvc.perform(delete("/Enrollment/deleteEnrollment/8"))
            .andExpect(status().isOk());

        verify(enrollmentService).deleteEnrollment(8L);
    }
}
