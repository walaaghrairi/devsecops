package tn.esprit.studentmanagement.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.studentmanagement.entities.Student;
import tn.esprit.studentmanagement.repositories.StudentRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentService studentService;

    private static Student student(Long id, String firstName) {
        Student student = new Student();
        student.setIdStudent(id);
        student.setFirstName(firstName);
        return student;
    }

    @Test
    @DisplayName("getAllStudents returns every student the repository holds")
    void returnsEveryStudent() {
        Student walaa = student(1L, "Walaa");
        when(studentRepository.findAll()).thenReturn(List.of(walaa));

        assertThat(studentService.getAllStudents()).containsExactly(walaa);
    }

    @Test
    @DisplayName("getStudentById returns the student when it exists")
    void returnsStudentById() {
        Student walaa = student(2L, "Walaa");
        when(studentRepository.findById(2L)).thenReturn(Optional.of(walaa));

        assertThat(studentService.getStudentById(2L)).isSameAs(walaa);
    }

    @Test
    @DisplayName("getStudentById returns null, not an exception, for an unknown id")
    void returnsNullForUnknownId() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(studentService.getStudentById(99L)).isNull();
    }

    @Test
    @DisplayName("saveStudent stores the entity and returns the saved copy")
    void savesStudent() {
        Student walaa = student(null, "Walaa");
        when(studentRepository.save(walaa)).thenReturn(walaa);

        assertThat(studentService.saveStudent(walaa)).isSameAs(walaa);
        verify(studentRepository).save(walaa);
    }

    @Test
    @DisplayName("deleteStudent deletes by id")
    void deletesStudent() {
        studentService.deleteStudent(3L);

        verify(studentRepository).deleteById(3L);
    }
}
