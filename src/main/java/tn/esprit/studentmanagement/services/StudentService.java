package tn.esprit.studentmanagement.services;

import org.springframework.stereotype.Service;
import tn.esprit.studentmanagement.entities.Student;
import tn.esprit.studentmanagement.repositories.StudentRepository;

import java.util.List;

@Service
public class StudentService implements IStudentService {
    private final StudentRepository studentRepository;

    // Constructor injection
    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public List<Student> getAllStudents() { return studentRepository.findAll(); }
    public Student getStudentById(Long id) { return studentRepository.findById(id).orElse(null); }
    public Student saveStudent(Student student) { return studentRepository.save(student); }
    public void deleteStudent(Long id) { studentRepository.deleteById(id); }

}
