import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyBuilder;

//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.google.code.gson:gson:2.10.1
//DEPS org.mnode.ical4j:ical4j:3.2.10
//JAVAC_OPTIONS -parameters

public class createIcal {

    static String eventUrl = "https://shop.doag.org/api/event/action.getMyLocationEvent/eventId.105";
    static final Gson GSON = new GsonBuilder().create();

    public static void main(String... args) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(eventUrl))
                .setHeader("accept-type", "application/json")
                .GET()
                .build();

        var http = HttpClient.newHttpClient();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("Event download failed: " + response.statusCode() + ": " + response.body());
        }

        var event = GSON.fromJson(response.body(), Map.class);
        var agenda = (Map<String, Map<String, Object>>) event.get("agenda");

        var components = agenda.entrySet().stream()
                .filter(i -> {
                    var title = i.getValue().get("title");
                    return title != null && !title.toString().trim().isBlank();
                }).map(item -> {
                    try {
                    var value = item.getValue();
                    var title = value.get("title").toString().trim();
                    var start = ((Number) value.get("start")).longValue();
                    var end = ((Number) value.get("end")).longValue();
                    var id = orEmpty(value.get("eventSlotId"));

                    var component = new VEvent(new DateTime(start * 1000), new DateTime(end * 1000), title);
                    component.withProperty(new PropertyBuilder().name(Property.UID).value("105."+id).build());

                    return (CalendarComponent) component;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).collect(Collectors.toCollection(ComponentList::new));

        var calendar = new Calendar(components);
        calendar.withProdId("CloudLand23")
                .withProperty(new PropertyBuilder().name(Property.TZID).value("Europe/Berlin").build());

        var ical = calendar.toString().replace("\r", "");

        Files.write(
                Paths.get("cloudland23.ical"),
                ical.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    static String orEmpty(Object value) {
        if (value == null) {
            return "";
        } else {
            return String.valueOf(value);
        }
    }

}
