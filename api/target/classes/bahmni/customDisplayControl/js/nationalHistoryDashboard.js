'use strict';

angular.module('bahmni.common.displaycontrol.custom')
    .directive('nationalHistoryDashboard', ['$http', '$timeout', 'appService', function ($http, $timeout, appService) {
        var link = function ($scope, element) {
            $scope.contentUrl = appService.configBaseUrl() + '/customDisplayControl/views/nationalHistoryDashboard.html';

            $scope.loading = true;
            $scope.error = false;
            $scope.records = [];
            $scope.fallbackRecords = [];

            var MAX_INJECT_RETRIES = 8;
            var RETRY_INTERVAL_MILLIS = 400;
            var INJECTED_NODE_CLASS = 'nationalhistory-injected';
            var INJECTED_LIST_PREFIX = 'nationalhistory-injected-list-';

            var closestDashboardSection = function (node) {
                var current = node;
                while (current && current !== document.body) {
                    if (current.classList && current.classList.contains('dashboard-section')) {
                        return current;
                    }
                    current = current.parentNode;
                }
                return null;
            };

            var hostDashboardSection = closestDashboardSection(element[0]);

            var setHostVisibility = function (visible) {
                if (!hostDashboardSection) {
                    return;
                }
                hostDashboardSection.style.display = visible ? '' : 'none';
            };

            var normalize = function (value) {
                return ((value || '') + '').replace(/\s+/g, ' ').trim().toUpperCase();
            };

            var removeInjectedNodes = function () {
                angular.forEach(document.querySelectorAll('.' + INJECTED_NODE_CLASS), function (node) {
                    if (node && node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                });
            };

            var mapRecordToTargetSection = function (recordType) {
                var type = normalize(recordType);

                if (type === 'DIAGNOSIS') {
                    return 'diagnoses';
                }
                if (type === 'CONDITION' || type === 'ALLERGY') {
                    return 'conditions';
                }
                if (type === 'RECENT VISIT' || type === 'ENCOUNTER' || type === 'VISIT') {
                    return 'visits';
                }
                if (type === 'APPOINTMENT') {
                    return 'appointments';
                }
                if (type === 'ATTACHMENT' || type === 'DOCUMENTREFERENCE' || type === 'DOCUMENT REFERENCE') {
                    return 'patientDocument';
                }

                return null;
            };

            var findSectionByHeader = function (headerCandidates) {
                var headings = document.querySelectorAll('.section-title');
                var found = null;

                angular.forEach(headings, function (heading) {
                    if (found) {
                        return;
                    }
                    var headingText = normalize(heading.textContent || heading.innerText);
                    angular.forEach(headerCandidates, function (candidate) {
                        if (found) {
                            return;
                        }
                        if (headingText.indexOf(normalize(candidate)) >= 0) {
                            found = closestDashboardSection(heading);
                        }
                    });
                });

                return found;
            };

            var ensureList = function (sectionNode, key) {
                var listClass = INJECTED_LIST_PREFIX + key;
                var list = sectionNode.querySelector('ul.' + listClass);

                if (!list) {
                    list = document.createElement('ul');
                    list.className = listClass + ' ' + INJECTED_NODE_CLASS;
                    list.style.margin = '6px 0 0 18px';
                    list.style.padding = '0';
                    list.style.listStyle = 'disc';
                    sectionNode.appendChild(list);
                }

                return list;
            };

            var appendRecord = function (list, record, label) {
                var summary = (record && record.summary) || 'N/A';
                var date = (record && record.date) || 'N/A';
                var facility = (record && record.facility) || 'N/A';

                var item = document.createElement('li');
                item.className = INJECTED_NODE_CLASS;
                item.style.margin = '4px 0';
                item.innerHTML =
                    '<span>' + _.escape(summary) + '</span>' +
                    ' <span style="color:#777;">(' + _.escape(date) + ', ' + _.escape(facility) + ')</span>' +
                    ' <span style="font-size:11px;color:#6b6b6b;">[' + _.escape(label) + ']</span>';
                list.appendChild(item);
            };

            var injectRecordsIntoDashboard = function (records) {
                var sectionTitleCount = document.querySelectorAll('.section-title').length;
                if (sectionTitleCount === 0) {
                    return {
                        waitForDom: true,
                        leftovers: records || []
                    };
                }

                var targets = {
                    diagnoses: findSectionByHeader(['Diagnoses']),
                    conditions: findSectionByHeader(['Conditions']),
                    visits: findSectionByHeader(['Visits']),
                    appointments: findSectionByHeader(['Appointments']),
                    patientDocument: findSectionByHeader(['Patient Document'])
                };

                var leftovers = [];

                angular.forEach(records || [], function (record) {
                    var targetKey = mapRecordToTargetSection(record && record.type);
                    if (!targetKey || !targets[targetKey]) {
                        leftovers.push(record);
                        return;
                    }

                    var list = ensureList(targets[targetKey], targetKey);
                    appendRecord(list, record, 'National');
                });

                return {
                    waitForDom: false,
                    leftovers: leftovers
                };
            };

            var injectWithRetry = function (records, attempt) {
                var result = injectRecordsIntoDashboard(records);
                if (result.waitForDom && attempt < MAX_INJECT_RETRIES) {
                    $timeout(function () {
                        injectWithRetry(records, attempt + 1);
                    }, RETRY_INTERVAL_MILLIS);
                    return;
                }

                $scope.loading = false;
                $scope.fallbackRecords = result.leftovers || [];

                // If everything was mapped to native Bahmni sections, hide this extra panel.
                if (($scope.records || []).length > 0 && $scope.fallbackRecords.length === 0) {
                    setHostVisibility(false);
                } else {
                    setHostVisibility(true);
                }
            };

            var loadNationalHistory = function () {
                removeInjectedNodes();
                setHostVisibility(true);

                if (!$scope.patient || !$scope.patient.uuid) {
                    $scope.loading = false;
                    $scope.error = true;
                    return;
                }

                var url = '/openmrs/ws/rest/v1/nationalhistory/' + $scope.patient.uuid;

                $http.get(url, {
                    withCredentials: true
                }).then(function (response) {
                    var data = response && response.data ? response.data : {};
                    $scope.records = data.records || [];
                    injectWithRetry($scope.records, 0);
                }, function () {
                    $scope.loading = false;
                    $scope.error = true;
                    setHostVisibility(true);
                });
            };

            loadNationalHistory();
        };

        return {
            restrict: 'E',
            link: link,
            scope: {
                patient: '=',
                section: '='
            },
            template: '<ng-include src="contentUrl"></ng-include>'
        };
    }]);
