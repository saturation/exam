(function () {
    'use strict';
    angular.module("sitnet.resources")
        .factory("QuestionRes", ['$resource', function ($resource) {
            return {
                questions: $resource("/questions/:id",
                {
                    id: "@id"
                },
                {
                    "update": {method: "PUT", params: {id: "@id"}},
                    "delete": {method: "DELETE", params: {id: "@id"}},
                    "create": {method: "POST"}

                }),
                options: $resource("/questions/:qid/option/:oid",
                {
                    qid: "@qid", oid: "@oid"
                },
                {
                    "update": {method: "PUT"},
                    "create": {method: "POST", params: {qid: "@qid"}},
                    "delete": {method: "DELETE", params: {oid: "@oid"}}
                })
            };
        }]);
}());
