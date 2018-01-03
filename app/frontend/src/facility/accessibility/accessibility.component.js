/*
 * Copyright (c) 2017 Exam Consortium
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl/licence-eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed
 * on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

'use strict';
angular.module('app.facility.accessibility')
    .component('accessibility', {
        templateUrl: '/assets/app/facility/accessibility/accessibility.template.html',
        controller: ['$translate', '$http', 'toast', function ($translate, $http, toast) {

            var ctrl = this;

            ctrl.$onInit = function () {
                ctrl.newItem = {};
                $http.get('/app/accessibility').then(function (resp) {
                    ctrl.accessibilities = resp.data;
                });
            };

            ctrl.add = function (item) {
                $http.post('/app/accessibility', item).then(function (resp) {
                    ctrl.accessibilities.push(resp.data);
                    toast.info($translate.instant("sitnet_accessibility_added"));
                });
            };

            ctrl.update = function (accessibility) {
                $http.put('/app/accessibility', accessibility).then(function () {
                    toast.info($translate.instant("sitnet_accessibility_updated"));
                });
            };

            ctrl.remove = function (accessibility) {
                $http.delete('/app/accessibility/' + accessibility.id).then(function () {
                    ctrl.accessibilities.splice(ctrl.accessibilities.indexOf(accessibility), 1);
                    toast.info($translate.instant("sitnet_accessibility_removed"));
                });
            };
        }]
    });