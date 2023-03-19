import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Contact;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.TzId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.model.DateTime;

import static java.time.temporal.ChronoField.*;

//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.google.code.gson:gson:2.10.1
//DEPS org.mnode.ical4j:ical4j:3.2.10
//JAVAC_OPTIONS -parameters
public class createIcal {

    static final String BERLIN = "Europe/Berlin";
    static final String AGENDA_URL = "https://shop.doag.org/events/cloudland/2023/agenda/#eventDay.all";
    static final Gson GSON = new GsonBuilder().create();

    /**
     * DateTime format used in iCAL, with offset mandatory for valid UTC conversion
     * handling
     */
    static DateTimeFormatter icsDateTimeFormat = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(YEAR, 4)
            .appendValue(MONTH_OF_YEAR, 2)
            .appendValue(DAY_OF_MONTH, 2)
            .appendLiteral('T')
            .appendValue(HOUR_OF_DAY, 2)
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendValue(SECOND_OF_MINUTE, 2)
            .append(new DateTimeFormatterBuilder().appendOffsetId().toFormatter())
            .parseStrict()
            .toFormatter();

    public static void main(String... args) throws Exception {
        var event = GSON.fromJson(Files.readString(Paths.get("data.json")), Map.class);
        var agenda = (Map<String, Map<String, Object>>) event.get("agenda");
        var rooms = (Map<String, Map<String, Object>>) event.get("rooms");

        var mixin = GSON.fromJson(Files.readString(Paths.get("mixin.json")), Map.class);
        var streams = (Map<String, Map<String, Object>>) mixin.get("streams");

        Function<Object, String> getRoomById = roomId -> {
            if (roomId instanceof String && !roomId.toString().isBlank()) {
                Map<String, Object> room = rooms.get(roomId);
                if (room != null) {
                    return orEmpty(room.get("name"));
                }
            }
            return "";
        };

        Function<Object, String> getSpeaker = speaker -> {
            if (speaker != null) {
                Map<String, String> map = (Map<String, String>) speaker;
                var name = map.get("name");
                var company = map.get("company");
                var speakerString = orEmpty(name);
                if (company != null && !company.isBlank()) {
                    speakerString += " (" + company.trim() + ")";
                }
                return speakerString;
            }
            return "";
        };

        Function<Object, String> getCoSpeaker = coSpeakers -> {
            if (coSpeakers != null && coSpeakers instanceof Collection) {
                return ((Collection<Map<String, String>>) coSpeakers).stream()
                        .map(getSpeaker)
                        .collect(Collectors.joining("\n"));
            }
            return "";
        };

        Function<Object, String> getStreamIcon = streamId -> {
            if (streamId != null && !orEmpty(streamId).isBlank()) {
                Map<String, Object> map = (Map<String, Object>) streams.get(streamId);
                if (map != null) {
                    return orEmpty(map.get("icon")) + " ";
                }
            }
            return "";
        };

        Function<Object, String> getStreamName = streamId -> {
            if (streamId != null && !orEmpty(streamId).isBlank()) {
                Map<String, String> map = (Map<String, String>) event.get("mainFocuses");
                return orEmpty(map.get(streamId));
            }
            return "";
        };

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
                        var icon = getStreamIcon.apply(value.get("mainFocus"));
                        var streamName = getStreamName.apply(value.get("mainFocus"));
                        var speaker = getSpeaker.apply(value.get("speaker"));
                        var coSpeaker = getCoSpeaker.apply(value.get("coSpeaker"));

                        var description = new StringBuilder();
                        if (!speaker.isBlank()) {
                            description.append(speaker);
                            description.append("\n");
                        }
                        if (!coSpeaker.isBlank()) {
                            description.append(coSpeaker);
                            description.append("\n");
                        }
                        if (description.length() > 0) {
                            description.append("\n");
                        }
                        if (!streamName.isBlank()) {
                            description.append(icon);
                            description.append(streamName);
                            description.append("\n");
                        }
                        if (description.length() > 0) {
                            description.append("\n");
                        }
                        description.append("TODO: Description of " + title);

                        var component = new VEvent(epochSecondsToDateTime(start), epochSecondsToDateTime(end), icon + title);
                        component.withProperty(new Uid("C-105." + item.getKey() + "." + id));
                        component.withProperty(new Location(getRoomById.apply(value.get("roomId"))));
                        component.withProperty(new TzId(BERLIN));
                        component.withProperty(new Contact(speaker));
                        component.withProperty(new Description(description.toString()));

                        return (CalendarComponent) component;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).collect(Collectors.toCollection(ComponentList::new));

        var calendar = new Calendar(components);
        calendar.withProdId("CloudLand23")
                .withProperty(Version.VERSION_2_0)
                .withProperty(new Url(new URI(AGENDA_URL)))
                .withProperty(new Description("CloudLand 2023 Lineup - Das Cloud Native(s) Festival"));

        var ical = calendar.toString();

        Files.write(
                Paths.get("cloudland.ics"),
                ical.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    static DateTime epochSecondsToDateTime(long epochSeconds) throws ParseException {
        return new DateTime(
                icsDateTimeFormat.format(
                        OffsetDateTime.ofInstant(
                                Instant.ofEpochSecond(epochSeconds), ZoneId.of(BERLIN))
                                .atZoneSameInstant(ZoneOffset.UTC)));
    }

    static String orEmpty(Object value) {
        if (value == null) {
            return "";
        } else {
            return String.valueOf(value).trim();
        }
    }

}
