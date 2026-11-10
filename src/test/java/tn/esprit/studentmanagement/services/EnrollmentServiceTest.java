package tn.esprit.studentmanagement.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.studentmanagement.entities.Enrollment;
import tn.esprit.studentmanagement.entities.Status;
import tn.esprit.studentmanagement.repositories.EnrollmentRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private static Enrollment enrollment(Long id, Status status) {
        Enrollment enrollment = new Enrollment();
        enrollment.setIdEnrollment(id);
        enrollment.setStatus(status);
        return enrollment;
    }

    @Test
    @DisplayName("getAllEnrollments returns every enrollment the repository holds")
    void returnsEveryEnrollment() {
        Enrollment active = enrollment(1L, Status.ACTIVE);
        when(enrollmentRepository.findAll()).thenReturn(List.of(active));

        assertThat(enrollmentService.getAllEnrollments()).containsExactly(active);
    }

    @Test
    @DisplayName("getEnrollmentById returns the enrollment when it exists")
    void returnsEnrollmentById() {
        Enrollment completed = enrollment(2L, Status.COMPLETED);
        when(enrollmentRepository.findById(2L)).thenReturn(Optional.of(completed));

        assertThat(enrollmentService.getEnrollmentById(2L)).isSameAs(completed);
    }

    @Test
    @DisplayName("getEnrollmentById returns null for an unknown id")
    void returnsNullForUnknownId() {
        when(enrollmentRepository.findById(1234L)).thenReturn(Optional.empty());

        assertThat(enrollmentService.getEnrollmentById(1234L)).isNull();
    }

    @Test
    @DisplayName("saveEnrollment stores the entity and returns the saved copy")
    void savesEnrollment() {
        Enrollment dropped = enrollment(null, Status.DROPPED);
        when(enrollmentRepository.save(dropped)).thenReturn(dropped);

        assertThat(enrollmentService.saveEnrollment(dropped)).isSameAs(dropped);
        verify(enrollmentRepository).save(dropped);
    }

    @Test
    @DisplayName("deleteEnrollment deletes by id")
    void deletesEnrollment() {
        enrollmentService.deleteEnrollment(11L);

        verify(enrollmentRepository).deleteById(11L);
    }
}
