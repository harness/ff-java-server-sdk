# Evaluations

When a user calls one of the Variation methods, the SDK performs an evaluation.  In the case of a client SDK it relies 
on the ff-server or proxy to perform the evaluation.  In the case of the server SDK it performs the evaluation itself.  
In either case the SDK should return the same value for a given target and flag.

## Default Variations Boolean
Feature: A flag with two default variations  
Given a <type of> Feature Flag with no rules  
And the default on variation is <on variation>  
And the default on variation is <off  variation>  
And the state is <state>  
Then the SDK should return the <expected> variation

| type of | on variation | off variation | state | expected | file             |
|---------|--------------|---------------|-------|----------|------------------|
| bool    | true         | false         | on    | true     | bool-flag-1.json |
| bool    | true         | false         | off   | false    | bool-flag-2.json |
| bool    | false        | true          | on    | false    | bool-flag-3.json |
| bool    | false        | true          | off   | true     | bool-flag-4.json |

## Default Variations Multivariate
Feature: A flag with at least three variations

Given a <type of> Feature Flag with no rules  
And the flag has these variations <variations>  
And the default on variation is <on variation>  
And the default off variation is <off  variation>  
And the state is <state>  
Then the SDK should return the <expected> variation

| type of | variations                                                                                                    | on variation                                            | off  variation                                          | state | expected                                                 |                     |
|---------|---------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|---------------------------------------------------------|-------|----------------------------------------------------------|---------------------|
| string  | one,two,three                                                                                                 | one                                                     | two                                                     | on    | one                                                      | string-flag-1.json  |
| string  | one,two,three                                                                                                 | one                                                     | two                                                     | off   | two                                                      | string-flag-2.json  |
| string  | one,two,three                                                                                                 | three                                                   | one                                                     | on    | three                                                    | string-flag-3.json  |
| string  | one,two,three                                                                                                 | three                                                   | one                                                     | off   | one                                                      | string-flag-4.json  |
| string  | 5, “five”, “five5”                                                                                            | 5                                                       | “five”                                                  | on    | 5                                                        | string-flag-5.json  |
| string  | 5, “five”, “five5”                                                                                            | 5                                                       | “five”                                                  | off   | “five”                                                   | string-flag-6.json  |
| string  | 5, “five”, “five5”,”6Six”                                                                                     | “five5”                                                 | ”6Six”                                                  | on    | “five5”                                                  | string-flag-7.json  |
| string  | 5, “five”, “five5”,”6Six”                                                                                     | “five5”                                                 | ”6Six”                                                  | off   | ”6Six”                                                   | string-flag-8.json  |
| string  | Rosetti quote Wilde quote Java                                                                                | Rosetti quote                                           | Wilde quote                                             | On    | Rosetti quote                                            | string-flag-9.json  |
| string  | Rosetti quote Wilde quote Java                                                                                | Rosetti quote                                           | Wilde quote                                             | off   | Wilde quote                                              | string-flag-10.json |
| string  | Rosetti quote Wilde quote Java                                                                                | Java                                                    | Wilde quote                                             | on    | java                                                     | string-flag-11.json |
| string  | $£-_><~ ^%&"'\/# ><~^%                                                                                        | $£-_><~                                                 | ^%&"'\/#                                                | on    | $£-_><~                                                  | string-flag-12.json |
| string  | $£-_><~ ^%&"'\/# ><~^%                                                                                        | $£-_><~                                                 | ^%&"'\/#                                                | off   | ^%&"'\/#                                                 | string-flag-13.json |
| string  | $£-_><~ ^%&"'\/# ><~^%                                                                                        | ><~^%                                                   | $£-_><~                                                 | on    | ><~^%                                                    | string-flag-14.json |
| string  | $£-_><~ ^%&"'\/# ><~^%                                                                                        | ><~^%                                                   | $£-_><~                                                 | off   | $£-_><~                                                  |                     |
| number  | 0.25,0.5,1                                                                                                    | 0.25                                                    | 1                                                       | on    | 0.25                                                     | number-flag-1.json  |
| number  | 0.25,0.5,1                                                                                                    | 0.25                                                    | 1                                                       | off   | 1                                                        | number-flag-2.json  |
| number  | 0.25,0.5,1                                                                                                    | 0.5                                                     | 0.25                                                    | on    | 0.5                                                      | number-flag-3.json  |
| number  | 0.25,0.5,1                                                                                                    | 0.5                                                     | 0.25                                                    | off   | 0.25                                                     | number-flag-4.json  |
| number  | 0.25,0.5,1.5625478965213255454589785451236541256321                                                           | 1.5625478965213255454589785451236541256321              | 0.25                                                    | on    | 1.5625478965213255454589785451236541256321               | number-flag-5.json  |
| json    | {} {   "number": "5",   "word": "five",   "char": "&^%$" }                                                    | {   "number": "5",   "word": "five",   "char": "&^%$" } | {}                                                      | on    | {   "number": "5",   "word": "five",   "lchar": "&^%$" } | json-flag-1.json    |
| json    | {} {   "number": "5",   "word": "five",   "char": "&^%$" } {   "number": "",   "": "five",   "char": "&^%$" } | {   "number": "5",   "word": "five",   "char": "&^%$" } | {}                                                      | on    | {   "number": "5",   "word": "five",   "char": "&^%$" }  | json-flag-2.json    |
| json    | {} {   "number": "5",   "word": "five",   "char": "&^%$" } {   "number": "",   "": "five",   "char": "&^%$" } | {   "number": "",   "": "five",   "lchar": "&^%$" }     | {   "number": "5",   "word": "five",   "char": "&^%$" } | off   | {   "number": "5",   "word": "five",   "char": "&^%$" }  | json-flag-3.json    |