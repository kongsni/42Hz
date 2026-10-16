package com.fortytwohz.room;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomService service;
    public RoomController(RoomService service) { this.service = service; }
    public record NicknameRequest(@NotBlank @Size(max=16) String nickname) {}
    public record AnswerRequest(@NotNull @Min(0) @Max(12) Integer questionIndex, @NotBlank @Size(max=1000) String answer) {}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public RoomService.CreateRoomResponse create(@Valid @RequestBody NicknameRequest body) { return service.create(body.nickname()); }
    @PostMapping("/{code}/join") @ResponseStatus(HttpStatus.CREATED)
    public RoomService.JoinRoomResponse join(@PathVariable String code, @Valid @RequestBody NicknameRequest body) { return service.join(code, body.nickname()); }
    @GetMapping("/{code}")
    public RoomService.RoomState state(@PathVariable String code, @RequestHeader(value="Authorization",required=false) String bearer) { return service.state(code, bearer); }
    @PostMapping("/{code}/answers") @ResponseStatus(HttpStatus.CREATED)
    public RoomService.AnswerState submit(@PathVariable String code, @RequestHeader(value="Authorization",required=false) String bearer, @Valid @RequestBody AnswerRequest body) { return service.submitAnswer(code, bearer, body.questionIndex(), body.answer()); }
    @GetMapping("/{code}/answers/{questionIndex}")
    public RoomService.AnswerState answers(@PathVariable String code, @PathVariable int questionIndex, @RequestHeader(value="Authorization",required=false) String bearer) { return service.answerState(code, bearer, questionIndex); }
    @GetMapping("/{code}/results")
    public RoomService.ResultsState results(@PathVariable String code, @RequestHeader(value="Authorization",required=false) String bearer) { return service.results(code, bearer); }
}
