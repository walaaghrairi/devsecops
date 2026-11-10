package tn.esprit.studentmanagement.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.esprit.studentmanagement.entities.Student;
import tn.esprit.studentmanagement.services.IStudentService;

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
 * Routing, binding and serialisation for the student endpoints. The service is a
 * mock, so these tests stay unit tests: no database, no Spring context, no port.
 */
class StudentControllerTest {

    private IStudentService studentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        studentService = mock(IStudentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StudentController(studentService)).build();
    }

    @Test
    @DisplayName("GET /students/getAllStudents returns the students as JSON")
    void listsStudents() throws Exception {
        Student walaa = new Student();
        walaa.setIdStudent(1L);
        walaa.setFirstName("Walaa");
        walaa.setLastName("Ghrairi");
        when(studentService.getAllStudents()).thenReturn(List.of(walaa));

        mockMvc.perform(get("/students/getAllStudents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].idStudent").value(1))
            .andExpect(jsonPath("$[0].firstName").value("Walaa"));
    }

    @Test
    @DisplayName("GET /students/getStudent/{id} passes the id to the service")
    void getsStudentById() throws Exception {
        Student walaa = new Student();
        walaa.setIdStudent(7L);
        when(studentService.getStudentById(7L)).thenReturn(walaa);

        mockMvc.perform(get("/students/getStudent/7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idStudent").value(7));
    }

    @Test
    @DisplayName("POST /students/createStudent binds the body and saves it")
    void createsStudent() throws Exception {
        when(studentService.saveStudent(any(Student.class))).thenAnswer(call -> {
            Student saved = call.getArgument(0);
            saved.setIdStudent(42L);
            return saved;
        });

        mockMvc.perform(post("/students/createStudent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Walaa\",\"lastName\":\"Ghrairi\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idStudent").value(42))
            .andExpect(jsonPath("$.firstName").value("Walaa"));
    }

    @Test
    @DisplayName("DELETE /students/deleteStudent/{id} delegates the id")
    void deletesStudent() throws Exception {
        mockMvc.perform(delete("/students/deleteStudent/5"))
            .andExpect(status().isOk());

        verify(studentService).deleteStudent(5L);
    }
}
