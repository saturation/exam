(function () {
    'use strict';
    angular.module("sitnet.resources")
        .factory("QuestionRes", ['$resource', function ($resource) {
            return $resource("/questions/:id",
                {
                    id: "@id"
                },
                {
                    "update": {
                        method: "PUT"
                    },

                    "delete": {
                        method: "DELETE",
                        params: {
                            id: "@id"
                        }
                    }
                }
            );
        }]);
}());