package tn.esprit.studentmanagement.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.esprit.studentmanagement.entities.Department;
import tn.esprit.studentmanagement.services.IDepartmentService;

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
 * Routing, binding and serialisation for the department endpoints. The base path
 * is the one the controller declares, typo included, so a rename cannot slip by.
 */
class DepartmentControllerTest {

    private IDepartmentService departmentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        departmentService = mock(IDepartmentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DepartmentController(departmentService)).build();
    }

    @Test
    @DisplayName("GET /Depatment/getAllDepartment returns the departments as JSON")
    void listsDepartments() throws Exception {
        Department informatics = new Department();
        informatics.setIdDepartment(1L);
        informatics.setName("Informatics");
        when(departmentService.getAllDepartments()).thenReturn(List.of(informatics));

        mockMvc.perform(get("/Depatment/getAllDepartment"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Informatics"));
    }

    @Test
    @DisplayName("GET /Depatment/getDepartment/{id} passes the id to the service")
    void getsDepartmentById() throws Exception {
        Department informatics = new Department();
        informatics.setIdDepartment(3L);
        when(departmentService.getDepartmentById(3L)).thenReturn(informatics);

        mockMvc.perform(get("/Depatment/getDepartment/3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idDepartment").value(3));
    }

    @Test
    @DisplayName("POST /Depatment/createDepartment binds the body and saves it")
    void createsDepartment() throws Exception {
        when(departmentService.saveDepartment(any(Department.class))).thenAnswer(call -> {
            Department saved = call.getArgument(0);
            saved.setIdDepartment(9L);
            return saved;
        });

        mockMvc.perform(post("/Depatment/createDepartment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Informatics\",\"location\":\"Tunis\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idDepartment").value(9))
            .andExpect(jsonPath("$.location").value("Tunis"));
    }

    @Test
    @DisplayName("DELETE /Depatment/deleteDepartment/{id} delegates the id")
    void deletesDepartment() throws Exception {
        mockMvc.perform(delete("/Depatment/deleteDepartment/6"))
            .andExpect(status().isOk());

        verify(departmentService).deleteDepartment(6L);
    }
}
