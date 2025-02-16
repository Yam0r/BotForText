package user.inn_bot.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import user.inn_bot.service.InnService;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Component
public class TelegramInnBot extends TelegramLongPollingBot {
    private final InnService innService;
    @Value("${BOT_TOKEN}")
    private String BOT_TOKEN;
    @Value("${BOT_NAME}")
    private String BOT_NAME;

    public TelegramInnBot(InnService innService) {
        this.innService = innService;
    }

    @Override
    public String getBotUsername() {
        return BOT_NAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();

            if (message.hasText()) {
                String messageText = message.getText().trim();

                if (messageText.equalsIgnoreCase("/start")) {
                    sendMainMenu(chatId);
                } else {
                    handleInn(chatId, messageText);
                }
            } else if (message.hasDocument()) {
                handleDocument(chatId, message);
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.startsWith("add_inn_")) {
                String inn = callbackData.replace("add_inn_", "");

                if (innService.isInnExists(inn)) {
                    sendTextMessage(chatId, "ИНН " + inn + " уже есть в базе.");
                } else {
                    innService.saveInn(inn);
                    sendTextMessage(chatId, "ИНН " + inn + " успешно добавлен в базу.");
                }
            } else if (callbackData.equals("cancel")) {
                sendTextMessage(chatId, "Добавление ИНН отменено.");
            }
        }
    }


    private void handleInn(long chatId, String inn) {
        String digitsOnlyInn = inn.replaceAll("[^\\d]", "");

        if (digitsOnlyInn.matches("\\d{10}")) {
            if (innService.isInnExists(digitsOnlyInn)) {
                sendTextMessage(chatId, "ИНН уже есть в базе.");
            } else {
                sendInlineKeyboard(chatId, digitsOnlyInn);
            }
        } else {
            int length = digitsOnlyInn.length();
            String digitForm = getDigitForm(length);

            sendTextMessage(chatId, "Вы написали " + length + " " + digitForm + ", а должно быть 10.");
        }
    }

    private String getDigitForm(int length) {
        if (length == 1) {
            return "цифра";
        } else if (length >= 2 && length <= 4) {
            return "цифры";
        } else {
            return "цифр";
        }
    }

    private void sendInlineKeyboard(long chatId, String inn) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("ИНН новый! Добавить его в базу?");

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton addButton = new InlineKeyboardButton("Добавить");
        addButton.setCallbackData("add_inn_" + inn);

        InlineKeyboardButton cancelButton = new InlineKeyboardButton("Отмена");
        cancelButton.setCallbackData("cancel");

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(addButton);
        row.add(cancelButton);

        rows.add(row);
        keyboardMarkup.setKeyboard(rows);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendTextMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMainMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Вам доступно:");

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton addButton = new InlineKeyboardButton("Добавить ИНН");
        addButton.setCallbackData("add_inn");

        InlineKeyboardButton searchButton = new InlineKeyboardButton("Проверка статуса ИНН");
        searchButton.setCallbackData("search_inn");

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(addButton);
        row.add(searchButton);

        rows.add(row);
        keyboardMarkup.setKeyboard(rows);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleDocument(long chatId, Message message) {
        String fileId = message.getDocument().getFileId();

        try {
            File file = execute(new org.telegram.telegrambots.meta.api.methods.GetFile(fileId));
            String filePath = file.getFilePath();
            String fileUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath;

            String fileName = message.getDocument().getFileName();
            String localFilePath = "downloads/" + fileName;
            downloadFile(fileUrl, localFilePath);

            List<String> innList = innService.extractInnsFromFile(localFilePath);
            FileWriter fileWriter = new FileWriter("downloads/processed_" + fileName);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            for (String inn : innList) {
                if (innService.isInnExists(inn)) {
                    bufferedWriter.write(inn + " - Уже существует в базе\n");
                } else {
                    innService.saveInn(inn);
                    bufferedWriter.write(inn + " - Успешно добавлен\n");
                }
            }

            bufferedWriter.close();
            sendTextMessage(chatId, "ИНН из файла успешно обработаны. Результаты в новом файле.");

        } catch (TelegramApiException | IOException e) {
            e.printStackTrace();
            sendTextMessage(chatId, "Не удалось загрузить файл.");
        }
    }

    private void downloadFile(String fileUrl, String filePath) {
        try (InputStream in = new URL(fileUrl).openStream();
             FileOutputStream out = new FileOutputStream(filePath)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
