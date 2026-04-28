<div id="nationalhistory-host" class="info-section">
    <div class="info-header">
        <i class="icon-book"></i>
        <h3>NATIONAL MEDICAL HISTORY</h3>
    </div>
    <div class="info-body">
        <div id="nationalhistory-loading">Loading national medical history...</div>
        <div id="nationalhistory-empty" style="display:none;">No national medical history available.</div>
        <div id="nationalhistory-error" style="display:none;">Unable to load national medical history.</div>
        <div id="nationalhistory-fallback-note" style="display:none; margin-bottom:8px;">Some national records could not be placed in local widgets, showing them here.</div>
        <ul id="nationalhistory-list" style="display:none; list-style:none; margin:0; padding:0;"></ul>
    </div>
</div>

<script type="text/javascript">
    (function () {
        var patientUuid = '${config.patient?.patient?.uuid ?: ""}';
        var endpoint = '/' + '${ui.contextPath()}' + '/ws/rest/v1/nationalhistory/' + patientUuid;

        function escapeHtml(value) {
            return jq('<div/>').text(value || '').html();
        }

        function renderFallbackRecords(records) {
            var list = jq('#nationalhistory-list');
            list.empty();

            jq.each(records, function (index, record) {
                var facility = record.facility || 'N/A';
                var type = record.type || 'N/A';
                var summary = record.summary || 'N/A';
                var date = record.date || 'N/A';

                var html = '' +
                    '<li style="margin-bottom:12px; padding-bottom:10px; border-bottom:1px solid #dddddd;">' +
                        '<div><strong>' + escapeHtml(facility) + '</strong></div>' +
                        '<div>Type: ' + escapeHtml(type) + '</div>' +
                        '<div>Summary: ' + escapeHtml(summary) + '</div>' +
                        '<div>Date: ' + escapeHtml(date) + '</div>' +
                    '</li>';

                list.append(html);
            });
        }

        function groupedRecords(records) {
            var groups = {
                conditions: [],
                visits: [],
                allergies: [],
                appointments: [],
                attachments: []
            };

            jq.each(records, function (index, record) {
                var type = ((record && record.type) || '').toUpperCase();
                if (type === 'DIAGNOSIS' || type === 'CONDITION') {
                    groups.conditions.push(record);
                } else if (type === 'RECENT VISIT' || type === 'ENCOUNTER') {
                    groups.visits.push(record);
                } else if (type === 'ALLERGY') {
                    groups.allergies.push(record);
                } else if (type === 'APPOINTMENT') {
                    groups.appointments.push(record);
                } else if (type === 'ATTACHMENT' || type === 'DOCUMENTREFERENCE') {
                    groups.attachments.push(record);
                } else {
                    groups.conditions.push(record);
                }
            });

            return groups;
        }

        function findSectionBodyByHeader(possibleHeaders) {
            var foundBody = null;
            jq('.info-section').each(function () {
                if (foundBody) {
                    return false;
                }
                var section = jq(this);
                if (section.attr('id') === 'nationalhistory-host') {
                    return;
                }

                var headerText = section.find('> .info-header h3').first().text();
                headerText = (headerText || '').toUpperCase();
                var match = false;
                jq.each(possibleHeaders, function (index, header) {
                    if (headerText.indexOf(header) >= 0) {
                        match = true;
                        return false;
                    }
                });

                if (match) {
                    foundBody = section.find('> .info-body').first();
                }
            });
            return foundBody;
        }

        function ensureInjectedList(body, listId) {
            var list = body.find('#' + listId);
            if (list.length === 0) {
                list = jq('<ul/>', {
                    id: listId,
                    'class': 'nationalhistory-injected-list',
                    style: 'list-style:none; margin-top:8px; padding-left:0;'
                });
                body.append(list);
            }
            list.empty();
            return list;
        }

        function appendRecordsToSection(body, listId, records, typeLabel) {
            if (!body || body.length === 0 || !records || records.length === 0) {
                return false;
            }

            var list = ensureInjectedList(body, listId);
            jq.each(records, function (index, record) {
                var summary = record.summary || 'N/A';
                var date = record.date || 'N/A';
                var facility = record.facility || 'N/A';
                var itemHtml = '' +
                    '<li style="margin:8px 0; border-left:3px solid #2f6f9f; padding:6px 8px; background:#f6f9fc;">' +
                        '<div><strong>' + escapeHtml(summary) + '</strong></div>' +
                        '<div style="font-size:0.9em;">' +
                            '<span>' + escapeHtml(typeLabel) + '</span> | ' +
                            '<span>' + escapeHtml(date) + '</span> | ' +
                            '<span>' + escapeHtml(facility) + '</span>' +
                        '</div>' +
                        '<div style="font-size:0.85em; color:#555;">Source: National</div>' +
                    '</li>';
                list.append(itemHtml);
            });

            return true;
        }

        function injectIntoLocalWidgets(records) {
            var groups = groupedRecords(records);
            var leftovers = [];

            var conditionsBody = findSectionBodyByHeader(['CONDITIONS', 'DIAGNOSES']);
            if (!appendRecordsToSection(conditionsBody, 'nationalhistory-conditions-injected', groups.conditions, 'Condition/Diagnosis')) {
                leftovers = leftovers.concat(groups.conditions);
            }

            var visitsBody = findSectionBodyByHeader(['RECENT VISITS', 'VISITS']);
            if (!appendRecordsToSection(visitsBody, 'nationalhistory-visits-injected', groups.visits, 'Recent Visit')) {
                leftovers = leftovers.concat(groups.visits);
            }

            var allergiesBody = findSectionBodyByHeader(['ALLERGIES']);
            if (!appendRecordsToSection(allergiesBody, 'nationalhistory-allergies-injected', groups.allergies, 'Allergy')) {
                leftovers = leftovers.concat(groups.allergies);
            }

            var appointmentsBody = findSectionBodyByHeader(['APPOINTMENTS']);
            if (!appendRecordsToSection(appointmentsBody, 'nationalhistory-appointments-injected', groups.appointments, 'Appointment')) {
                leftovers = leftovers.concat(groups.appointments);
            }

            var attachmentsBody = findSectionBodyByHeader(['ATTACHMENTS']);
            if (!appendRecordsToSection(attachmentsBody, 'nationalhistory-attachments-injected', groups.attachments, 'Attachment')) {
                leftovers = leftovers.concat(groups.attachments);
            }

            return leftovers;
        }

        if (!patientUuid) {
            jq('#nationalhistory-loading').hide();
            jq('#nationalhistory-error').text('Unable to determine patient identifier for national medical history.').show();
            return;
        }

        jq.ajax({
            url: endpoint,
            method: 'GET',
            dataType: 'json'
        }).done(function (response) {
            jq('#nationalhistory-loading').hide();

            if (!response || !response.records || response.records.length === 0) {
                jq('#nationalhistory-empty').show();
                return;
            }

            var leftovers = injectIntoLocalWidgets(response.records);
            if (leftovers.length > 0) {
                renderFallbackRecords(leftovers);
                jq('#nationalhistory-fallback-note').show();
                jq('#nationalhistory-list').show();
            } else {
                jq('#nationalhistory-host').hide();
            }
        }).fail(function () {
            jq('#nationalhistory-loading').hide();
            jq('#nationalhistory-error').show();
        });
    })();
</script>
