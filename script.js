var options = {
    groupOrder: function (a, b) {
        return a.id - b.id;
    },
    editable: false,
    groupHeightMode: 'fixed',
    zoomMax: 64800000,
    zoomMin: 3600000
};

async function loadData() {
    const data = await fetch("data.json")
        .then(response => response.json())
        .catch(error => console.log(error));
    const mixin = await fetch("mixin.json")
        .then(response => response.json())
        .catch(error => console.log(error));

    parseData(data, mixin);
}

function parseData(data, mixin) {
    const groupList = Object.entries(data.rooms)
        .map(([roomId, room]) => (
            {
                id: roomId,
                content: groupContent(room)
            }
        ));
    var groups = new vis.DataSet(groupList);

    const itemList = Object.entries(data.agenda)
        .filter(([itemId, item]) => item.title && item.title.trim() !== "")
        .map(([itemId, item]) => (
            {
                id: itemId,
                group: item.roomId,
                content: itemContent(item, data.mainFocuses, mixin.streams),
                start: new Date(item.start * 1000),
                end: new Date(item.end * 1000)
            }
        ));
    var items = new vis.DataSet(itemList);

    createTimeline(options, groups, items);
}

function formatSpeaker(speaker) {
    const name = speaker.name;
    var company = "";
    if (speaker.company && speaker.company.trim() !== "") {
        company = `<i>(${speaker.company})</i>`;
    }
    return `<h6 class="card-subtitle mb-2 text-muted">${name} ${company}</h6>`;
}

function itemContent(item, streamData, streamMixin) {
    var title = item.title.trim();
    var speakers = new Array();
    if (item.speaker) {
        speakers.push(formatSpeaker(item.speaker));
    }
    if (item.coSpeaker) {
        item.coSpeaker.forEach(speaker =>
            speakers.push(formatSpeaker(speaker))
        );
    }
    var speakerLines = speakers.join('\n');

    var stream = "";
    if (item.mainFocus) {
        var icon = "";
        if (streamMixin[item.mainFocus]) {
            icon = streamMixin[item.mainFocus].icon;
        }
        var streamName = "";
        if (streamData[item.mainFocus]) {
            streamName = streamData[item.mainFocus];
        }
        stream = `${icon} ${streamName}`;
    }

    var html = `
        <h5 class="card-title">${title}</h5>
        ${speakerLines}
        <p class="card-text">${stream}</p>
    `;

    var element = document.createElement('div');
    element.innerHTML = html;
    return element;
}

function groupContent(room) {
    var group = document.createElement('div');
    var html = `
        <h3>${room.name.trim()}</h3><br/>
        <span>🪑 ${room.capacity}</span>
    `;

    group.innerHTML = html;
    return group;
}

function createTimeline(options, groups, items) {
    var container = document.getElementById('visualization');
    var timeline = new vis.Timeline(container);
    timeline.setOptions(options);
    timeline.setGroups(groups);
    timeline.setItems(items);
}

function init() {
    loadData();
}