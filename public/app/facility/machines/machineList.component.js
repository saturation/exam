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
angular.module('app.facility.machines')
    .component('machineList', {
        templateUrl: '/assets/app/facility/machines/machineList.template.html',
        bindings: {
            room: '<'
        },
        controller: ['Machines', '$translate', 'toast', function (Machines, $translate, toast) {

            var vm = this;

            vm.$onInit = function () {
                vm.showMachines = true;
            };

            vm.toggleShow = function () {
                vm.showMachines = !vm.showMachines
            };

            vm.countMachineAlerts = function () {
                if (!vm.room) return 0;
                return vm.room.examMachines.filter(function (m) {
                    return m.outOfService;
                }).length;
            };

            vm.countMachineNotices = function () {
                if (!vm.room) return 0;
                return vm.room.examMachines.filter(function (m) {
                    return !m.outOfService && m.statusComment;
                }).length;
            };

            vm.addNewMachine = function () {
                var newMachine = {};

                Machines.machine.insert({id: vm.room.id}, newMachine, function (machine) {
                    toast.info($translate.instant("sitnet_machine_added"));
                    vm.room.examMachines.push(machine);
                }, function (error) {
                    toast.error(error.data);
                });
            };

        }]
    });