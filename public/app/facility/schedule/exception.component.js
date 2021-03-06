/*
 * Copyright (c) 2017 The members of the EXAM Consortium (https://confluence.csc.fi/display/EXAM/Konsortio-organisaatio)
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
angular.module('app.facility.schedule')
    .component('exception', {
        templateUrl: '/assets/app/facility/schedule/exception.template.html',
        bindings: {
            close: '&',
            dismiss: '&',
            resolve: '<'
        },
        controller: ['$translate', 'toast', function ($translate, toast) {

            var vm = this;

            vm.$onInit = function () {
                var now = new Date();
                now.setMinutes(0);
                now.setSeconds(0);
                now.setMilliseconds(0);
                vm.dateOptions = {
                    'starting-day': 1
                };
                vm.dateFormat = 'dd.MM.yyyy';

                vm.exception = {startDate: now, endDate: angular.copy(now), outOfService: true};
            };

            vm.ok = function () {
                var start = moment(vm.exception.startDate);
                var end = moment(vm.exception.endDate);
                if (end <= start) {
                    toast.error($translate.instant('sitnet_endtime_before_starttime'));
                    return;
                }
                vm.close({
                    $value: {
                        "startDate": start,
                        "endDate": end,
                        "outOfService": vm.exception.outOfService
                    }
                });
            };

            vm.cancel = function () {
                vm.dismiss({$value: 'cancel'});
            };
        }]
    });