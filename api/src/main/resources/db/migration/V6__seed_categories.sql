-- T03.7 Default categories (user_id NULL). Sinhala names need native-speaker review (T39.4).
INSERT INTO categories (user_id, name, name_si, kind, need_or_want) VALUES
    (NULL, 'Salary',          'වැටුප',                 'INCOME',  NULL),
    (NULL, 'Allowance',       'දීමනාව',                'INCOME',  NULL),
    (NULL, 'Part-time',       'අර්ධකාලීන රැකියාව',      'INCOME',  NULL),
    (NULL, 'Other',           'වෙනත්',                 'INCOME',  NULL),
    (NULL, 'Food',            'ආහාර',                  'EXPENSE', 'NEED'),
    (NULL, 'Food delivery',   'ආහාර බෙදාහැරීම',         'EXPENSE', 'WANT'),
    (NULL, 'Transport',       'ප්‍රවාහනය',              'EXPENSE', 'NEED'),
    (NULL, 'Rent',            'නිවාස කුලිය',             'EXPENSE', 'NEED'),
    (NULL, 'Utilities',       'විදුලි / ජල බිල්පත්',      'EXPENSE', 'NEED'),
    (NULL, 'Mobile/Internet', 'දුරකථන / අන්තර්ජාලය',     'EXPENSE', 'NEED'),
    (NULL, 'Shopping',        'සාප්පු සවාරි',            'EXPENSE', 'WANT'),
    (NULL, 'Entertainment',   'විනෝදාස්වාදය',           'EXPENSE', 'WANT'),
    (NULL, 'Health',          'සෞඛ්‍යය',                'EXPENSE', 'NEED'),
    (NULL, 'Education',       'අධ්‍යාපනය',              'EXPENSE', 'NEED'),
    (NULL, 'Loan payment',    'ණය ගෙවීම',              'EXPENSE', 'NEED'),
    (NULL, 'Savings',         'ඉතිරිකිරීම්',             'EXPENSE', NULL),
    (NULL, 'Other',           'වෙනත්',                 'EXPENSE', NULL);
