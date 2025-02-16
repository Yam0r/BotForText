package user.inn_bot.service;

import lombok.RequiredArgsConstructor;
import user.inn_bot.model.Inn;
import org.springframework.stereotype.Service;
import user.inn_bot.repository.InnRepository;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class InnService {

    private final InnRepository innRepository;

    public boolean isInnExists(String inn) {
        return innRepository.existsByInn(inn);
    }

    public void saveInn(String inn) {
        if (innRepository.existsByInn(inn)) {
            throw new IllegalArgumentException("ИНН уже существует в базе данных");
        }

        Inn newInn = new Inn();
        newInn.setInn(inn);

        ZonedDateTime currentTimeInUkraine = ZonedDateTime.now(ZoneId.of("Europe/Kiev"));
        newInn.setCreatedAt(currentTimeInUkraine.toLocalDateTime());

        innRepository.save(newInn);
    }

    public List<String> extractInnsFromFile(String filePath) {
        List<String> innList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.matches("\\d{10}")) {
                    innList.add(line.trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return innList;
    }

    private List<String> extractInnsFromLine(String line) {
        List<String> innList = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\b\\d{10}\\b");
        Matcher matcher = pattern.matcher(line);

        while (matcher.find()) {
            innList.add(matcher.group());
        }

        return innList;
    }
}
